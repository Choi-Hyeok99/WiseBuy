package com.sparta.order.service;

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
public class OrderProducerService {

    private final KafkaTemplate<String, KafkaMessage<?>> kafkaTemplate; //  KafkaMessage<?> 타입으로 변경

    public <T> void sendMessage(String topic, KafkaMessage<T> message, String key) {
        kafkaTemplate.send(topic, key, message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Kafka 발행 실패 - topic: {}, key: {}, message: {}", topic, key, message, ex);
            }
        });
    }

    /**
     * 현재 트랜잭션이 커밋된 뒤에 발행한다. 롤백되면 발행하지 않는다.
     *
     * 예전엔 @Transactional 안에서 바로 send해서 DB가 롤백돼도 이벤트는 이미 나가 있었다.
     * (예: 주문 저장은 롤백됐는데 product는 order.create를 받아 DB 재고를 차감 / 만료 처리가 락 충돌로
     *  롤백됐는데 stock.rollback은 이미 나가서 결제 완료된 주문의 재고가 복구됨)
     *
     * 반대로 커밋 후 발행 자체가 실패하면(브로커 장애 등) 이벤트가 유실될 수 있다 - 실패는 로그로 남긴다.
     * DB 커밋과 발행을 원자적으로 묶으려면 Outbox 패턴이 필요하다(README 한계 참고).
     */
    public <T> void sendAfterCommit(String topic, KafkaMessage<T> message, String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            sendMessage(topic, message, key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                sendMessage(topic, message, key);
            }
        });
    }
}
