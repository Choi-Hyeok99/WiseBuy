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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
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
        // 1. 위시리스트 확인
        List<WishlistItemDto> wishlistItems = fetchWishlist(userId);

        // 2. 주문 엔티티 생성
        Order order = createNewOrder(userId, address);

        // 3. 주문 항목 추가 및 재고 감소
        int totalAmount = processOrderItems(wishlistItems, order);

        // 4. 총 금액 업데이트
        order.setTotalAmount(totalAmount);
        orderRepository.save(order);

        // 5. order.create 이벤트 발행 (상품별 1건).
        //    재고 예약(Redis)은 위 processOrderItems에서 이미 끝났고, 이 이벤트는 product 쪽 배치 컨슈머가
        //    묶어서 DB에 반영하는 데 쓴다. 그래서 orderId만이 아니라 productId/quantity를 실어 보낸다.
        //    파티션 키를 productId로 두어 같은 상품 이벤트가 한 파티션에 모이도록(순서/배치 효율) 한다.
        for (OrderItem orderItem : order.getOrderItems()) {
            orderProducerService.sendMessage(
                    "order.create",
                    new KafkaMessage<>("ORDER_CREATED", stockEventPayload(order.getId(), orderItem.getProductId(), orderItem.getQuantity())),
                    String.valueOf(orderItem.getProductId()));
        }

        // 6. 응답 DTO 반환
        return new OrderResponseDto(order);
    }
    @Transactional
    public void updateOrderStatusAfterPayment(Long orderId, boolean isPaymentSuccessful, Long userId) {
        // 주문 조회
        Order order = orderRepository.findById(orderId)
                                     .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다."));

        // 사용자 ID 검증
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("사용자 ID가 일치하지 않습니다.");
        }

        // 상태 업데이트 로직
        if (isPaymentSuccessful) {
            order.setStatus(OrderStatus.PAYMENT_COMPLETED); // 결제 완료 상태로 설정
            order.setOrderDate(LocalDateTime.now()); // 결제 완료 시간 기록
        } else {
            order.setStatus(OrderStatus.PAYMENT_FAILED); // 결제 실패
        }

        orderRepository.save(order); // 변경 저장
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
            throw new InvalidOrderStateException("배송 중 상태에서는 주문을 취소할 수 없습니다.");
        }

        // 주문 상태를 '취소'로 변경
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelDate(LocalDateTime.now());

        // 주문 항목 상태 변경 및 재고 복구
        for (OrderItem item : order.getOrderItems()) {
            item.setStatus(OrderItemStatus.RETURNED); // 상태 변경 (상품이 반품됨)

            // 상품 정보 조회 및 재고 복구
            ProductResponseDto product = productClient.getProductById(item.getProductId());

            if (product == null) {
                throw new IllegalArgumentException("상품을 찾을 수 없습니다. 상품 ID: " + item.getProductId());
            }

            // 재고 복구: Redis는 즉시(sync), DB는 order.create 배치 컨슈머로.
            // quantity 규약이 "차감할 수량(양수)"이므로 복구는 음수를 보낸다
            // (product Lua는 DECRBY라 DECRBY -n = +n; 배치 컨슈머도 stock - (-n) = +n).
            // 예전엔 여기서 양수를 보내 취소할수록 재고가 더 줄었다.
            productClient.updateStock(item.getProductId(), new StockUpdateRequestDto(-item.getQuantity()));
            orderProducerService.sendMessage(
                    "order.create",
                    new KafkaMessage<>("ORDER_CANCELLED", stockEventPayload(order.getId(), item.getProductId(), -item.getQuantity())),
                    String.valueOf(item.getProductId()));
        }

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
        // 위시리스트 가져오기
        List<WishlistItemDto> wishlistItems = wishlistClient.getWishlist(userId);

        // 위시리스트 비어 있는지 확인
        if (wishlistItems.isEmpty()) {
            throw new IllegalArgumentException("위시리스트가 비어 있습니다.");
        }

        // 위시리스트 상품 상태와 재고 확인
        for (WishlistItemDto item : wishlistItems) {
            // 상품 정보 가져오기
            ProductResponseDto product = productClient.getProductById(item.getProductId());
            if (product == null) {
                throw new IllegalArgumentException("상품 정보를 찾을 수 없습니다. ID: " + item.getProductId());
            }

            // 상품 상태 확인
            if (!"AVAILABLE".equals(product.getStatus())) {
                throw new IllegalStateException("상품이 구매 불가능한 상태입니다. 상품 ID: " + item.getProductId());
            }

            // 상품 재고 확인
            if (product.getStock() < item.getQuantity()) {
                throw new IllegalStateException("상품 재고가 부족합니다. 상품 ID: " + item.getProductId());
            }
        }

        return wishlistItems;
    }

    // order.create 이벤트 payload. product 배치 컨슈머가 productId/quantity를 읽어 DB 재고를 반영한다.
    // quantity: 양수 = 차감(주문), 음수 = 복구(취소).
    private Map<String, Object> stockEventPayload(Long orderId, Long productId, int quantity) {
        Map<String, Object> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("productId", productId);
        data.put("quantity", quantity);
        return data;
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
    private int processOrderItems(List<WishlistItemDto> wishlistItems, Order order) {
        int totalAmount = 0;

        for (WishlistItemDto item : wishlistItems) {
            // 1. 제품 정보 가져오기
            ProductResponseDto product = productClient.getProductById(item.getProductId());
            if (product == null) {
                throw new IllegalArgumentException("상품 정보를 찾을 수 없습니다. ID: " + item.getProductId());
            }

            // 2. 제품 상태 확인
            if (!"AVAILABLE".equals(product.getStatus())) {
                throw new IllegalArgumentException("상품이 구매 가능한 상태가 아닙니다. 상품 ID: " + item.getProductId());
            }

            // 3. 재고 확인
            if (product.getStock() < item.getQuantity()) {
                throw new IllegalArgumentException("상품의 재고가 부족합니다. 상품 ID: " + item.getProductId() + ", 남은 재고: " + product.getStock());
            }

            // 4. 주문 항목 생성
            OrderItem orderItem = createOrderItem(order, product, item);
            totalAmount += orderItem.getPrice() * orderItem.getQuantity();

            // 5. 재고 차감 요청
            // updateStock의 quantity 규약은 "차감할 수량(양수)"이다. product 쪽 Lua가 DECRBY로 그만큼 빼고,
            // stock.update 컨슈머도 (stock - quantity)로 계산한다. 예전엔 여기서 -item.getQuantity()(음수)를
            // 보내서 DECRBY -n = +n 이 되어 주문할수록 재고가 오히려 늘어나고, 재고 부족 체크(stock < -n)도
            // 항상 통과하는 버그가 있었다. 양수로 바로잡음.
            productClient.updateStock(product.getId(), new StockUpdateRequestDto(item.getQuantity()));
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
