package com.sparta.order.scheduler;

import com.sparta.order.entity.OrderStatus;
import com.sparta.order.repository.OrderRepository;
import com.sparta.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 결제 대기 시간이 지난 주문을 만료시키고 예약 재고를 돌려놓는다.
 *
 * 주문 생성 시 Redis 재고는 바로 예약되는데, 사용자가 결제창에서 이탈하면 주문이 PENDING으로 남아
 * 선착순 재고가 영원히 묶인다. 주문마다 별도 트랜잭션(OrderService.expireOrder)으로 처리해서
 * 한 건의 충돌/실패가 다른 주문 만료를 막지 않게 한다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private static final List<OrderStatus> AWAITING_PAYMENT = List.of(OrderStatus.PENDING, OrderStatus.PAYMENT_PENDING);

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @Value("${order.payment-timeout-minutes:10}")
    private long paymentTimeoutMinutes;

    @Scheduled(fixedDelayString = "${order.expiration-check-interval-ms:60000}", initialDelay = 30000)
    public void expireUnpaidOrders() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(paymentTimeoutMinutes);
        List<Long> orderIds = orderRepository.findIdsByStatusInAndOrderDateBefore(AWAITING_PAYMENT, before);
        if (orderIds.isEmpty()) {
            return;
        }
        log.info("결제 대기 시간({}분) 초과 주문 {}건 만료 처리 시작", paymentTimeoutMinutes, orderIds.size());

        for (Long orderId : orderIds) {
            try {
                orderService.expireOrder(orderId);
            } catch (ObjectOptimisticLockingFailureException e) {
                // 결제 결과/취소가 동시에 반영됨. 롤백됐으므로 재고 복구도 안 나갔다 - 다음 주기에 상태를 다시 본다.
                log.info("주문 ID {} 만료 처리 중 상태 변경 충돌 - 다음 주기에 재확인", orderId);
            } catch (Exception e) {
                log.error("주문 ID {} 만료 처리 실패", orderId, e);
            }
        }
    }
}
