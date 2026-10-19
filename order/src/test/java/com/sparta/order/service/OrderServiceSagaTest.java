package com.sparta.order.service;

import com.sparta.common.dto.KafkaMessage;
import com.sparta.common.dto.ProductResponseDto;
import com.sparta.common.dto.WishlistItemDto;
import com.sparta.order.client.PaymentClient;
import com.sparta.order.client.ProductClient;
import com.sparta.order.client.WishlistClient;
import com.sparta.order.dto.StockUpdateRequestDto;
import com.sparta.order.entity.Order;
import com.sparta.order.entity.OrderItem;
import com.sparta.order.entity.OrderStatus;
import com.sparta.order.repository.OrderItemRepository;
import com.sparta.order.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * SAGA 보상 규칙 검증. 트랜잭션은 TransactionSynchronizationManager로 직접 흉내낸다
 * (커밋 = afterCommit + afterCompletion(COMMITTED), 롤백 = afterCompletion(ROLLED_BACK)).
 * KafkaTemplate만 mock이고 OrderProducerService는 진짜라서 "커밋 후 발행"까지 같이 검증된다.
 */
@SuppressWarnings("unchecked")
class OrderServiceSagaTest {

    private final OrderRepository orderRepository = mock(OrderRepository.class);
    private final WishlistClient wishlistClient = mock(WishlistClient.class);
    private final ProductClient productClient = mock(ProductClient.class);
    private final KafkaTemplate<String, KafkaMessage<?>> kafkaTemplate = mock(KafkaTemplate.class);

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, wishlistClient, mock(OrderItemRepository.class),
                productClient, mock(PaymentClient.class), new OrderProducerService(kafkaTemplate));
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            if (o.getId() == null) {
                o.setId(100L);
            }
            return o;
        });
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private void commit() {
        List<TransactionSynchronization> syncs = TransactionSynchronizationManager.getSynchronizations();
        syncs.forEach(TransactionSynchronization::afterCommit);
        syncs.forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
    }

    private void rollback() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
    }

    private List<Map<String, Object>> sentPayloads(String topic) {
        ArgumentCaptor<KafkaMessage<?>> captor = ArgumentCaptor.forClass(KafkaMessage.class);
        verify(kafkaTemplate, atLeast(0)).send(eq(topic), anyString(), captor.capture());
        return captor.getAllValues().stream().map(m -> (Map<String, Object>) m.getData()).toList();
    }

    private static ProductResponseDto product(long id) {
        return new ProductResponseDto(id, "p" + id, 10, 1000, null, null, null, "AVAILABLE");
    }

    private static Order orderWithItems(OrderStatus status) {
        Order order = new Order();
        order.setId(7L);
        order.setUserId(1L);
        order.setStatus(status);
        for (long productId : new long[]{1L, 2L}) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(productId);
            item.setQuantity(2);
            order.getOrderItems().add(item);
        }
        return order;
    }

    @Nested
    @DisplayName("주문 생성 - 다중 상품 부분 실패 보상")
    class CreateOrder {

        @BeforeEach
        void twoItemWishlist() {
            when(wishlistClient.getWishlist(1L)).thenReturn(List.of(
                    new WishlistItemDto(1L, "p1", 1000, null, 2),
                    new WishlistItemDto(2L, "p2", 1000, null, 3)));
            when(productClient.getProductById(1L)).thenReturn(product(1L));
            when(productClient.getProductById(2L)).thenReturn(product(2L));
        }

        @Test
        @DisplayName("A 예약 성공 후 B 예약 실패 → 롤백 시 시도한 A·B 모두 해제 요청(restoreDb=false), order.create는 안 나감")
        void releasesAttemptedItemsOnRollback() {
            // B가 409든 타임아웃이든 order는 실제 차감 여부를 모른다 → 시도한 항목 전부 해제를 보내고,
            // product가 주문별 예약 기록으로 실제 예약된 A만 되돌린다.
            doThrow(new IllegalStateException("재고 부족"))
                    .when(productClient).updateStock(eq(2L), any(StockUpdateRequestDto.class));

            assertThatThrownBy(() -> orderService.createOrder(null, 1L, "addr"))
                    .isInstanceOf(IllegalStateException.class);
            rollback();

            List<Map<String, Object>> rollbacks = sentPayloads("stock.rollback");
            assertThat(rollbacks)
                    .extracting(p -> p.get("productId"))
                    .containsExactlyInAnyOrder(1L, 2L);
            assertThat(rollbacks).allSatisfy(p -> assertThat(p)
                    .containsEntry("orderId", 100L)
                    .containsEntry("restoreDb", false));
            assertThat(sentPayloads("order.create")).isEmpty();
        }

        @Test
        @DisplayName("예약 요청에 orderId를 실어 보낸다 (product가 주문별 예약 기록을 남기는 키)")
        void sendsOrderIdWithReservation() {
            orderService.createOrder(null, 1L, "addr");

            verify(productClient).updateStock(1L, new StockUpdateRequestDto(100L, 2));
            verify(productClient).updateStock(2L, new StockUpdateRequestDto(100L, 3));
        }

        @Test
        @DisplayName("예약은 다 성공했는데 커밋이 실패해도 전부 해제된다")
        void releasesAllWhenCommitFails() {
            orderService.createOrder(null, 1L, "addr");
            rollback(); // 커밋 단계 실패

            assertThat(sentPayloads("stock.rollback"))
                    .extracting(p -> p.get("productId"))
                    .containsExactlyInAnyOrder(1L, 2L);
            assertThat(sentPayloads("order.create")).isEmpty();
        }

        @Test
        @DisplayName("정상 커밋 → order.create만 커밋 후 발행, 보상 없음")
        void publishesOrderCreateOnlyAfterCommit() {
            orderService.createOrder(null, 1L, "addr");
            assertThat(sentPayloads("order.create")).isEmpty(); // 커밋 전엔 아무것도 안 나감

            commit();

            assertThat(sentPayloads("order.create")).hasSize(2);
            assertThat(sentPayloads("stock.rollback")).isEmpty();
        }
    }

    @Nested
    @DisplayName("결제 결과 반영 - 상태 전이 가드")
    class ApplyPaymentResult {

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"PENDING", "PAYMENT_PENDING"})
        @DisplayName("결제 대기 중 + 실패 → PAYMENT_FAILED, 커밋 후 전 항목 재고 복구(restoreDb=true)")
        void failedPaymentCompensates(OrderStatus status) {
            Order order = orderWithItems(status);
            when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

            orderService.applyPaymentResult(7L, false);
            assertThat(sentPayloads("stock.rollback")).isEmpty();
            commit();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
            assertThat(sentPayloads("stock.rollback"))
                    .hasSize(2)
                    .allSatisfy(p -> assertThat(p).containsEntry("orderId", 7L).containsEntry("restoreDb", true));
        }

        @Test
        @DisplayName("결제 대기 중 + 성공 → PAYMENT_COMPLETED, 보상 없음")
        void successfulPayment() {
            Order order = orderWithItems(OrderStatus.PENDING);
            when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

            orderService.applyPaymentResult(7L, true);
            commit();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_COMPLETED);
            assertThat(sentPayloads("stock.rollback")).isEmpty();
        }

        @ParameterizedTest
        @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "EXPIRED", "PAYMENT_FAILED", "PAYMENT_COMPLETED"})
        @DisplayName("이미 끝난 주문에 늦게/중복으로 온 결제 결과는 무시 (재고 중복 복구 없음, 상태 유지)")
        void ignoresLateOrDuplicateResults(OrderStatus status) {
            Order order = orderWithItems(status);
            when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

            orderService.applyPaymentResult(7L, false);
            orderService.applyPaymentResult(7L, true);
            commit();

            assertThat(order.getStatus()).isEqualTo(status);
            assertThat(sentPayloads("stock.rollback")).isEmpty();
            verify(orderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("결제 대기 만료")
    class ExpireOrder {

        @Test
        @DisplayName("결제 대기 중 → EXPIRED, 커밋 후 재고 복구")
        void expiresAwaitingOrder() {
            Order order = orderWithItems(OrderStatus.PAYMENT_PENDING);
            when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

            orderService.expireOrder(7L);
            commit();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
            assertThat(sentPayloads("stock.rollback")).hasSize(2);
        }

        @Test
        @DisplayName("만료 트랜잭션이 롤백되면(@Version 충돌 등) 재고 복구도 안 나간다")
        void noCompensationWhenExpireRollsBack() {
            when(orderRepository.findById(7L)).thenReturn(Optional.of(orderWithItems(OrderStatus.PENDING)));

            orderService.expireOrder(7L);
            rollback();

            assertThat(sentPayloads("stock.rollback")).isEmpty();
        }

        @Test
        @DisplayName("조회 후 그 사이 결제 완료된 주문은 건드리지 않는다")
        void skipsAlreadyPaidOrder() {
            Order order = orderWithItems(OrderStatus.PAYMENT_COMPLETED);
            when(orderRepository.findById(7L)).thenReturn(Optional.of(order));

            orderService.expireOrder(7L);
            commit();

            assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_COMPLETED);
            assertThat(sentPayloads("stock.rollback")).isEmpty();
        }
    }

    @Test
    @DisplayName("사용자 취소 → Redis를 즉시 건드리지 않고, 커밋 후 stock.rollback으로만 복구")
    void cancelUsesStockRollbackAfterCommit() {
        Order order = orderWithItems(OrderStatus.PENDING);
        when(orderRepository.findByIdAndUserId(7L, 1L)).thenReturn(Optional.of(order));

        orderService.cancelOrder(7L, 1L);
        verify(productClient, never()).updateStock(any(), any());
        assertThat(sentPayloads("stock.rollback")).isEmpty();
        commit();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(sentPayloads("stock.rollback"))
                .hasSize(2)
                .allSatisfy(p -> assertThat(p).containsEntry("restoreDb", true));
        assertThat(sentPayloads("order.create")).isEmpty();
    }
}
