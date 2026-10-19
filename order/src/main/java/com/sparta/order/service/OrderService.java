package com.sparta.order.service;

import com.sparta.common.dto.KafkaMessage;
import com.sparta.common.dto.OrderResponseForPaymentDto;
import com.sparta.common.dto.ProductResponseDto;
import com.sparta.common.dto.WishlistItemDto;
import com.sparta.order.client.PaymentClient;
import com.sparta.order.client.WishlistClient;
import com.sparta.order.client.ProductClient;
import com.sparta.order.dto.*;
import com.sparta.order.entity.Order;
import com.sparta.order.entity.OrderItem;
import com.sparta.order.entity.OrderItemStatus;
import com.sparta.order.entity.OrderStatus;
import com.sparta.order.exception.InvalidOrderStateException;
import com.sparta.order.exception.OrderNotFoundException;
import com.sparta.order.repository.OrderItemRepository;
import com.sparta.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final WishlistClient wishlistClient; // Feign Client
    private final OrderItemRepository orderItemRepository;
    private final ProductClient productClient; // Feign Client
    private final PaymentClient paymentClient;
    private final OrderProducerService orderProducerService;


    @Transactional
    public OrderResponseDto createOrder(OrderRequestDto requestDto, Long userId, String address) {
        // 1. 위시리스트 + 상품 정보/재고 검증 (상품별 getProductById 1회, 모든 항목을 재고 차감 전에 먼저 검증)
        List<WishlistItemDto> wishlistItems = fetchWishlist(userId);
        Map<Long, ProductResponseDto> productById = validateProducts(wishlistItems);

        // 2. 주문 엔티티 생성
        Order order = createNewOrder(userId, address);

        // 3. 주문 항목 추가 및 재고 감소 (위에서 조회한 상품 정보 재사용 — 재조회 안 함)
        //    예약을 시도한 항목을 기록해두고, 이 트랜잭션이 롤백되면 그 항목들의 Redis 예약을 해제한다.
        List<OrderItem> attemptedItems = new ArrayList<>();
        releaseReservationsOnRollback(order.getId(), attemptedItems);
        int totalAmount = processOrderItems(wishlistItems, order, productById, attemptedItems);

        // 4. 총 금액 업데이트
        order.setTotalAmount(totalAmount);
        orderRepository.save(order);

        // 5. order.create 이벤트 발행 (상품별 1건, 주문이 커밋된 뒤에만).
        //    재고 예약(Redis)은 위 processOrderItems에서 이미 끝났고, 이 이벤트는 product 쪽 배치 컨슈머가
        //    묶어서 DB에 반영하는 데 쓴다. 그래서 orderId만이 아니라 productId/quantity를 실어 보낸다.
        //    파티션 키를 productId로 두어 같은 상품 이벤트가 한 파티션에 모이도록(순서/배치 효율) 한다.
        for (OrderItem orderItem : order.getOrderItems()) {
            orderProducerService.sendAfterCommit(
                    "order.create",
                    new KafkaMessage<>("ORDER_CREATED", stockEventPayload(order.getId(), orderItem.getProductId(), orderItem.getQuantity())),
                    String.valueOf(orderItem.getProductId()));
        }

        // 6. 응답 DTO 반환
        return new OrderResponseDto(order);
    }
    /**
     * payment.result 반영. 결제 실패면 SAGA 보상으로 재고 복구(stock.rollback)를 발행한다.
     *
     * 상태 전이 가드: 결제 대기 중(PENDING/PAYMENT_PENDING)인 주문에만 반영한다. 예전엔 주문 상태와 상관없이
     * 덮어써서, 이미 취소된 주문에 결제 실패가 늦게 오면 재고가 한 번 더 복구됐다.
     * 결제 결과 이벤트가 재전달(at-least-once)돼도 두 번째부터는 이 가드에 걸려 무시된다.
     */
    @Transactional
    public void applyPaymentResult(Long orderId, boolean isPaymentSuccessful) {
        Order order = orderRepository.findById(orderId)
                                     .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다 ID: " + orderId));

        if (!order.getStatus().isAwaitingPayment()) {
            if (isPaymentSuccessful && order.getStatus() == OrderStatus.PAYMENT_COMPLETED) {
                log.info("이미 결제 완료된 주문의 결제 성공 이벤트(재전달) - 주문 ID {} (무시)", orderId);
            } else if (isPaymentSuccessful) {
                // 결제는 됐는데 주문은 이미 취소/만료됨. 재고는 이미 풀렸으므로 주문을 되살리지 않고 환불 대상으로 남긴다.
                // (payment에 환불 API가 없어 지금은 로그로만 남긴다 - README 한계 참고)
                log.error("결제 성공 이벤트를 받았지만 주문이 이미 {} 상태 - 주문 ID {} (환불 필요)", order.getStatus(), orderId);
            } else {
                log.warn("이미 {} 상태인 주문의 결제 실패 이벤트 - 주문 ID {} (무시, 재고 중복 복구 방지)", order.getStatus(), orderId);
            }
            return;
        }

        if (isPaymentSuccessful) {
            order.setStatus(OrderStatus.PAYMENT_COMPLETED);
        } else {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            publishStockRollbackAfterCommit(order);
            log.info("결제 실패 -> 주문 취소, 재고 롤백 이벤트 발행 : 주문 ID {}", orderId);
        }
        orderRepository.save(order);
        log.info("주문 ID {} 상태 업데이트 : {}", orderId, order.getStatus());
    }

    /**
     * 결제 대기 시간 초과 주문을 만료시키고 재고를 되돌린다 (OrderExpirationScheduler가 주문 단위로 호출).
     * 결제창에서 이탈한 주문이 PENDING으로 남으면 선착순 재고가 영원히 묶이기 때문.
     *
     * 조회 시점과 처리 시점 사이에 결제 결과가 먼저 반영됐을 수 있어서 상태를 다시 확인한다.
     * 정말 동시에 반영되면 @Version 충돌로 한쪽이 롤백되고, 롤백되면 stock.rollback도 나가지 않는다(커밋 후 발행).
     */
    @Transactional
    public void expireOrder(Long orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || !order.getStatus().isAwaitingPayment()) {
            return;
        }
        order.setStatus(OrderStatus.EXPIRED);
        order.setCancelDate(LocalDateTime.now());
        publishStockRollbackAfterCommit(order);
        orderRepository.save(order);
        log.info("결제 대기 시간 초과 -> 주문 만료, 재고 롤백 이벤트 발행 : 주문 ID {}", orderId);
    }


    @Transactional
    public List<OrderResponseDto> getOrders(Long userId) {
        List<Order> orders = orderRepository.findAllByUserId(userId);

        return orders.stream().map(order -> {
            // OrderItem -> OrderItemResponseDto 변환
            List<OrderItemResponseDto> items = order.getOrderItems().stream().map(item -> {
                // ProductClient로 productName 가져오기
                ProductResponseDto product = productClient.getProductById(item.getProductId());
                if (product == null) {
                    throw new IllegalArgumentException("상품 정보를 찾을 수 없습니다. ID: " + item.getProductId());
                }
                return OrderItemResponseDto.from(item, product.getProductName());
            }).toList();

            // Order -> OrderResponseDto 변환
            return OrderResponseDto.from(order, items);
        }).toList();
    }

    @Transactional
    public void cancelOrder(Long orderId, Long userId) {
        // 주문 ID와 사용자 ID로 주문 조회
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                                     .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다."));

        // 주문 상태 확인 (PENDING 상태에서만 취소 가능)
        if (!order.getStatus().equals(OrderStatus.PENDING)) {
            throw new InvalidOrderStateException("결제 전(PENDING) 주문만 취소할 수 있습니다.");
        }

        // 주문 상태를 '취소'로 변경
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelDate(LocalDateTime.now());

        // 주문 항목 상태 변경
        for (OrderItem item : order.getOrderItems()) {
            item.setStatus(OrderItemStatus.RETURNED); // 상태 변경 (상품이 반품됨)
        }

        // 재고 복구는 결제 실패·만료와 같은 stock.rollback 경로로 통일하고, 취소가 커밋된 뒤에만 발행한다.
        // 예전엔 여기서 Redis를 Feign으로 즉시 복구하고 DB는 order.create(음수 수량)로 보냈는데,
        // (1) 취소가 커밋 전에 실패해도 Redis는 이미 복구돼 재고가 늘어날 수 있었고
        // (2) order.create 멱등키가 {orderId}:{productId}라 주문 생성 이벤트와 키가 겹쳐 DB 복구가 "중복"으로 건너뛰어졌다.
        publishStockRollbackAfterCommit(order);

        // 변경된 주문 저장
        orderRepository.save(order);
    }

    @Transactional
    public void returnOrder(Long orderId, Long userId) {
        // 주문 ID와 사용자 ID로 주문 조회
        Order order = orderRepository.findByIdAndUserId(orderId, userId)
                                     .orElseThrow(() -> new OrderNotFoundException("해당 주문을 찾을 수 없습니다."));

        // 배송 완료 상태(COMPLETED) 확인
        if (!order.getStatus().equals(OrderStatus.COMPLETED)) {
            throw new IllegalStateException("배송 완료된 주문만 반품 요청을 할 수 있습니다.");
        }

        // 반품 가능 시간 확인
        LocalDateTime now = LocalDateTime.now();
        if (order.getDeliveryDate() == null || now.isAfter(order.getDeliveryDate().plusMinutes(5))) {
            throw new IllegalStateException("반품 가능 시간이 초과되었습니다.");
        }

        // 주문 상태를 '반품 요청(RETURN_REQUESTED)'으로 변경
        order.setStatus(OrderStatus.RETURN_REQUESTED);

        // 주문 항목 상태를 '반품 요청(RETURN_REQUESTED)'으로 변경
        for (OrderItem item : order.getOrderItems()) {
            item.setStatus(OrderItemStatus.RETURN_REQUESTED);
        }

        // 변경된 주문 저장
        orderRepository.save(order);
    }


    private List<WishlistItemDto> fetchWishlist(Long userId) {
        List<WishlistItemDto> wishlistItems = wishlistClient.getWishlist(userId);
        if (wishlistItems.isEmpty()) {
            throw new IllegalArgumentException("위시리스트가 비어 있습니다.");
        }
        return wishlistItems;
    }

    /**
     * 위시리스트 각 상품을 한 번씩만 조회해 상태·재고를 검증하고, productId → 상품정보 맵으로 돌려준다.
     * 예전엔 이 검증 루프(fetchWishlist)와 아래 processOrderItems가 각자 getProductById를 호출해
     * 상품당 2회 조회했다. 검증은 모든 항목에 대해 재고 차감 전에 끝나므로 fail-fast는 그대로 유지된다.
     */
    private Map<Long, ProductResponseDto> validateProducts(List<WishlistItemDto> wishlistItems) {
        Map<Long, ProductResponseDto> productById = new HashMap<>();
        for (WishlistItemDto item : wishlistItems) {
            ProductResponseDto product = productClient.getProductById(item.getProductId());
            if (product == null) {
                throw new IllegalArgumentException("상품 정보를 찾을 수 없습니다. ID: " + item.getProductId());
            }
            if (!"AVAILABLE".equals(product.getStatus())) {
                throw new IllegalStateException("상품이 구매 불가능한 상태입니다. 상품 ID: " + item.getProductId());
            }
            if (product.getStock() < item.getQuantity()) {
                throw new IllegalStateException("상품 재고가 부족합니다. 상품 ID: " + item.getProductId());
            }
            productById.put(item.getProductId(), product);
        }
        return productById;
    }

    // order.create 이벤트 payload. product 배치 컨슈머가 productId/quantity를 읽어 DB 재고를 반영한다.
    // quantity: 양수 = 차감(주문). 복구는 stock.rollback으로 따로 보낸다.
    private Map<String, Object> stockEventPayload(Long orderId, Long productId, int quantity) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("productId", productId);
        data.put("quantity", quantity);
        return data;
    }

    /**
     * 주문 트랜잭션이 롤백되면 그때까지 예약을 시도한 재고를 해제한다 (다중 상품 주문의 부분 실패 보상).
     *
     * 예: A 예약 성공 → B 재고 부족(409) → 주문 롤백. DB는 롤백되지만 A의 Redis 예약은 그대로 남아 재고가 샜다.
     * 예외를 catch하는 대신 트랜잭션 완료 콜백을 쓰는 이유는, 예약은 다 성공했는데 커밋에서 실패하는 경우까지
     * 같이 잡기 위해서다. order.create는 커밋 후에만 나가므로 이때 DB 재고는 아직 차감 전 → Redis만 되돌린다.
     *
     * 성공한 항목만이 아니라 "시도한" 항목 전부를 보낸다. 예약 호출이 타임아웃이면 결과를 모르기 때문이다.
     * product가 주문별 예약 기록으로 실제 예약된 것만 되돌리고, 해제가 예약보다 먼저 도착해도 툼스톤으로 막는다.
     */
    private void releaseReservationsOnRollback(Long orderId, List<OrderItem> reservedItems) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_ROLLED_BACK || reservedItems.isEmpty()) {
                    return;
                }
                log.warn("주문 생성 롤백 -> Redis 재고 예약 {}건 해제 : 주문 ID {}", reservedItems.size(), orderId);
                for (OrderItem item : reservedItems) {
                    orderProducerService.sendMessage("stock.rollback",
                            stockRollbackMessage(orderId, item, false), String.valueOf(item.getProductId()));
                }
            }
        });
    }

    // SAGA 보상: 주문의 모든 항목 재고를 Redis·DB 모두 되돌린다. 커밋된 뒤에만 발행.
    // product는 (orderId, productId)로 멱등 처리하므로 같은 주문의 보상이 재전달돼도 한 번만 복구된다.
    private void publishStockRollbackAfterCommit(Order order) {
        for (OrderItem item : order.getOrderItems()) {
            orderProducerService.sendAfterCommit("stock.rollback",
                    stockRollbackMessage(order.getId(), item, true), String.valueOf(item.getProductId()));
        }
    }

    // restoreDb=false: DB에는 아직 차감이 반영되지 않은 경우(order.create 발행 전 롤백) - Redis 예약만 되돌린다.
    private KafkaMessage<Map<String, Object>> stockRollbackMessage(Long orderId, OrderItem item, boolean restoreDb) {
        return new KafkaMessage<>("STOCK_ROLLBACK", Map.of(
                "orderId", orderId,
                "productId", item.getProductId(),
                "quantity", item.getQuantity(),
                "restoreDb", restoreDb
        ));
    }

    private Order createNewOrder(Long userId, String address) {
        Order order = new Order();
        order.setUserId(userId);
        order.setShippingAddress(address);
        order.setOrderDate(LocalDateTime.now());
        order.setDeliveryDate(LocalDateTime.now().plusMinutes(10));
        order.setStatus(OrderStatus.PENDING);
        return orderRepository.save(order);
    }
    private int processOrderItems(List<WishlistItemDto> wishlistItems, Order order,
                                  Map<Long, ProductResponseDto> productById, List<OrderItem> attemptedItems) {
        int totalAmount = 0;

        for (WishlistItemDto item : wishlistItems) {
            // validateProducts에서 이미 조회·검증한 상품 정보를 재사용 (getProductById 재호출 안 함)
            ProductResponseDto product = productById.get(item.getProductId());

            // 주문 항목 생성
            OrderItem orderItem = createOrderItem(order, product, item);
            totalAmount += orderItem.getPrice() * orderItem.getQuantity();

            // 재고 차감 요청
            // updateStock의 quantity 규약은 "차감할 수량(양수)"이다. product 쪽 Lua가 DECRBY로 그만큼 빼고,
            // stock.update 컨슈머도 (stock - quantity)로 계산한다. 예전엔 여기서 -item.getQuantity()(음수)를
            // 보내서 DECRBY -n = +n 이 되어 주문할수록 재고가 오히려 늘어나고, 재고 부족 체크(stock < -n)도
            // 항상 통과하는 버그가 있었다. 양수로 바로잡음.
            // 호출 "전에" 기록한다. 타임아웃이면 product에서 실제로 차감됐는지 알 수 없는데, 그런 항목도
            // 해제 대상에 넣어야 재고가 새지 않는다. product는 주문별 예약 기록이 있을 때만 되돌리므로 안전하다.
            attemptedItems.add(orderItem);
            productClient.updateStock(product.getId(), new StockUpdateRequestDto(order.getId(), item.getQuantity()));
        }

        return totalAmount;
    }
    public OrderResponseForPaymentDto getOrderById(Long id, Long userId) {
        Order order = orderRepository.findByIdAndUserId(id, userId)
                                     .orElseThrow(() -> new IllegalArgumentException("해당 주문이 없거나 사용자 ID가 일치하지 않습니다."));
        return new OrderResponseForPaymentDto(order.getId(), order.getUserId(), order.getTotalAmount());
    }
    @Transactional
    public void preparePayment(Long orderId, Long userId){
        // 주문 조회
        Order order = orderRepository.findByIdAndUserId(orderId,userId)
                .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다."));

        // 주문 상태 검증
        if (!order.getStatus() .equals(OrderStatus.PENDING)){
            throw new InvalidOrderStateException("결제를 준비할 수 없는 주문 상태입니다.");
        }

        // 주문 상태 변경
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        orderRepository.save(order);
    }

    private OrderItem createOrderItem(Order order, ProductResponseDto product, WishlistItemDto item) {
        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProductId(product.getId());
        orderItem.setQuantity(item.getQuantity());
        orderItem.setPrice(product.getPrice());
        orderItem.setStatus(OrderItemStatus.ORDERED);
        order.getOrderItems().add(orderItem);
        return orderItem;
    }

}
