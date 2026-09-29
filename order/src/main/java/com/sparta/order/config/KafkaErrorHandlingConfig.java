package com.sparta.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * payment.result 컨슈머(OrderConsumerService)가 예외를 밖으로 던지면 1초 간격으로 최대 3회
 * 재시도하고, 그래도 실패하면 원본 메시지를 payment.result.DLT 로 보낸다.
 *
 * 이 빈을 등록하는 것만으로 스프링 부트가 자동 구성한 기본 리스너 컨테이너 팩토리(payment.result가
 * 쓰는 것)에 자동 적용된다 - 별도 팩토리 빈을 새로 만들 필요가 없다.
 *
 * 주문이 아예 없는 경우(IllegalArgumentException)는 재시도해도 결과가 같은 비즈니스 예외라
 * addNotRetryableExceptions로 등록해 무의미한 재시도 없이 바로 DLT로 보낸다.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        FixedBackOff backOff = new FixedBackOff(1000L, 3L); // 1초 간격, 최대 3회 재시도
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
