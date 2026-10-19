package com.sparta.payment.service;

import com.sparta.common.dto.KafkaMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentProducerService {

    private final KafkaTemplate<String, KafkaMessage<?>> kafkaTemplate;

    // 결제 결과 이벤트 발행 ( success or failed ). 결제 기록이 커밋된 뒤에만 보낸다.
    // 예전엔 트랜잭션 안에서 바로 보내서, 결제 저장이 롤백돼도 order는 결제 결과를 받아 주문 상태를 바꿨다.
    public <T> void sendPaymentResult(String topic, KafkaMessage<T> message){
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(topic, message);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(topic, message);
            }
        });
    }

    private <T> void send(String topic, KafkaMessage<T> message) {
        kafkaTemplate.send(topic, message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Kafka 발행 실패 - topic: {}, message: {}", topic, message, ex);
            }
        });
    }
}
