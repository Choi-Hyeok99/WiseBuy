package com.sparta.payment.service;

import com.sparta.common.dto.KafkaMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentProducerService {

    private final KafkaTemplate<String, KafkaMessage<?>> kafkaTemplate;

    // 결제 결과 이벤트 발행 ( success or failed )
    public <T> void sendPaymentResult(String topic, KafkaMessage<T> message){
        kafkaTemplate.send(topic,message);
    }
}
