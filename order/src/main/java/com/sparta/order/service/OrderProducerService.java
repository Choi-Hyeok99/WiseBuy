package com.sparta.order.service;

import com.sparta.common.dto.KafkaMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderProducerService {

    private final KafkaTemplate<String, KafkaMessage<?>> kafkaTemplate; //  KafkaMessage<?> 타입으로 변경

    public <T> void sendMessage(String topic, KafkaMessage<T> message) {
        kafkaTemplate.send(topic, message); //  JSON 변환 없이 바로 전송
    }
}