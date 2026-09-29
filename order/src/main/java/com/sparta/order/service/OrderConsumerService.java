package com.sparta.order.service;

import com.sparta.common.dto.KafkaMessage;
import com.sparta.order.entity.Order;
import com.sparta.order.entity.OrderItem;
import com.sparta.order.entity.OrderStatus;
import com.sparta.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderConsumerService {

    private final OrderRepository orderRepository;
    private final OrderProducerService orderProducerService;

    @KafkaListener(topics = "payment.result", groupId = "order-service")
    @Transactional
    public void consumePaymentResult(KafkaMessage<Map<String, Object>> message){
        log.info("Kafka 결제 이벤트 수신:  {}", message);

        Map<String, Object> dataMap = message.getData();
        Long orderId = ((Number) dataMap.get("orderId")).longValue();
        String paymentStatus = (String) dataMap.get("status");

        Order order;
        try {
            order = orderRepository.findById(orderId)
                                   .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다 ID: " + orderId));
        } catch (IllegalArgumentException e) {
            // 주문이 아예 없으면 재시도해도 결과가 같다 - 비즈니스 예외로 보고 로그만 남기고 종료(재시도 대상 아님)
            log.warn("payment.result 처리 대상 주문 없음 - {} (재시도 무의미, 건너뜀)", orderId, e);
            return;
        }

        if ("SUCCESS".equals(paymentStatus)){
            order.setStatus(OrderStatus.PAYMENT_COMPLETED);
        } else {
            order.setStatus(OrderStatus.PAYMENT_FAILED);

            // 결제 실패시, stock.rollback 이벤트 발행 ( 재고 복구 ). orderId를 함께 실어서
            // product 쪽에서 (orderId, productId) 조합으로 멱등 처리를 할 수 있게 한다.
            for (OrderItem item : order.getOrderItems()) {
                KafkaMessage<Map<String, Object>> rollbackMessage = new KafkaMessage<>("STOCK_ROLLBACK", Map.of(
                        "orderId", orderId,
                        "productId", item.getProductId(),
                        "quantity", item.getQuantity()
                ));
                orderProducerService.sendMessage("stock.rollback", rollbackMessage,String.valueOf(item.getProductId()));
            }
            log.info("결제 실패 -> 주문 취소, 재고 롤백 이벤트 발행 : 주문 ID {}", orderId);
        }
        orderRepository.save(order);
        log.info("주문 ID {} 상태 업데이트 : {}", orderId, order.getStatus());
        // 그 외 예외(파싱 오류, DB 저장 실패 등 인프라성)는 여기서 잡지 않고 그대로 전파한다.
        // KafkaErrorHandlingConfig의 DefaultErrorHandler가 재시도 후 DLT로 보낸다.
    }
}
