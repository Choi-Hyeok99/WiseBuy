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

        try{
            Map<String, Object> dataMap = message.getData();
            Long orderId = ((Number) dataMap.get("orderId")).longValue();
            String paymentStatus = (String) dataMap.get("status");

            Order order = orderRepository.findById(orderId)
                                         .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다 ID: " +  orderId));

            if ("SUCCESS".equals(paymentStatus)){
                order.setStatus(OrderStatus.PAYMENT_COMPLETED);
            } else {
                order.setStatus(OrderStatus.PAYMENT_FAILED);

                // 결제 실패시, stock.rollback 이벤트 발행 ( 재고 복구 )
                for (OrderItem item : order.getOrderItems()) {
                    KafkaMessage<Map<String, Object>> rollbackMessage = new KafkaMessage<>("STOCK_ROLLBACK", Map.of(
                            "productId", item.getProductId(),
                            "quantity", item.getQuantity()
                    ));
                    orderProducerService.sendMessage("stock.rollback", rollbackMessage,String.valueOf(item.getProductId()));
                }
                log.info("결제 실패 -> 주문 취소, 재고 롤백 이벤트 발행 : 주문 ID {}", orderId);
            }
            orderRepository.save(order);
            log.info("주문 ID {} 상태 업데이트 : ", orderId, order.getStatus());
        } catch (Exception e){
            log.error("Kafka 결제 이벤트 처리 중 오류 발생 : ", e);
        }
    }
}
