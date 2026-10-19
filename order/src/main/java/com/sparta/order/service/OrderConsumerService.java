package com.sparta.order.service;

import com.sparta.common.dto.KafkaMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderConsumerService {

    private final OrderService orderService;

    @KafkaListener(topics = "payment.result", groupId = "order-service")
    public void consumePaymentResult(KafkaMessage<Map<String, Object>> message){
        log.info("Kafka 결제 이벤트 수신:  {}", message);

        Map<String, Object> dataMap = message.getData();
        Long orderId = ((Number) dataMap.get("orderId")).longValue();
        String paymentStatus = (String) dataMap.get("status");

        try {
            orderService.applyPaymentResult(orderId, "SUCCESS".equals(paymentStatus));
        } catch (IllegalArgumentException e) {
            // 주문이 아예 없으면 재시도해도 결과가 같다 - 비즈니스 예외로 보고 로그만 남기고 종료(재시도 대상 아님)
            log.warn("payment.result 처리 대상 주문 없음 - {} (재시도 무의미, 건너뜀)", orderId, e);
        }
        // 그 외 예외(파싱 오류, DB 저장 실패, 만료 스케줄러와의 @Version 충돌 등)는 그대로 전파한다.
        // KafkaErrorHandlingConfig의 DefaultErrorHandler가 재시도하고, 재시도 때는 바뀐 주문 상태를 다시 읽어
        // 상태 전이 가드에 걸린다. 끝까지 실패하면 DLT로 보낸다.
    }
}
