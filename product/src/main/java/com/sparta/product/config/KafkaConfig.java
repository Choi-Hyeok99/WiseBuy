package com.sparta.product.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.clients.admin.NewTopic;
import com.sparta.common.dto.KafkaMessage;
import com.sparta.product.exception.NotFoundException;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    // order.create를 파티션 3개로 만든다(기존이 1개면 KafkaAdmin이 3으로 늘려줌).
    // order가 이벤트를 productId로 키잉하므로, 같은 상품 이벤트는 한 파티션에 모여 순서가 보장되고,
    // 서로 다른 상품은 파티션에 흩어져 컨슈머 3개가 병렬로 재고를 반영할 수 있다.
    @Bean
    public NewTopic orderCreateTopic() {
        return TopicBuilder.name("order.create").partitions(3).replicas(1).build();
    }

    // application.yml의 spring.kafka.bootstrap-servers(도커에서는 KAFKA_BOOTSTRAP_SERVERS=kafka:29092)를 그대로 쓴다.
    // 예전엔 여기에 "localhost:9092"가 코드로 직접 박혀 있어서, application.yml을 아무리 고쳐도
    // 이 컨슈머는 항상 자기 자신(localhost)에서만 Kafka를 찾으려 해 도커에서 절대 연결이 안 됐다.
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, KafkaMessage<Map<String, Object>>> batchConsumerFactory() {
        Map<String, Object> config = new HashMap<>();

        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "product-service");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");


        // 한 번에 최대 20개 메시지를 가져오도록 설정
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "20");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false"); // 자동 커밋 비활성화

        return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), new JsonDeserializer<>(KafkaMessage.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, KafkaMessage<Map<String, Object>>> batchFactory(
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, KafkaMessage<Map<String, Object>>> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(batchConsumerFactory());
        factory.setBatchListener(true);   // 배치 모드
        factory.setConcurrency(3);        // 파티션 3개를 컨슈머 스레드 3개가 병렬 소비
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    /**
     * 리스너가 예외를 밖으로 던지면(비즈니스 예외는 각 리스너가 안에서 잡아 걸러낸다) 1초 간격으로
     * 최대 3회 재시도하고, 그래도 실패하면 원본 메시지를 {topic}.DLT 로 보낸다(DeadLetterPublishingRecoverer).
     * 이전엔 리스너가 모든 예외를 안에서 삼켜서 이 핸들러까지 오지도 못했다 - 실패 메시지가 "정상 처리"로
     * 간주돼 오프셋이 그냥 커밋되고 조용히 유실되던 문제를 막는다.
     *
     * NotFoundException처럼 재시도해도 결과가 똑같은 비즈니스 예외는 addNotRetryableExceptions로 등록해
     * 무의미한 재시도 없이 바로 DLT로 보낸다(재시도와 비즈니스 예외를 구분).
     *
     * batchFactory(order.create)에 쓰면 배치 전체가 재시도 대상이 된다(레코드 단위 아님) - 이미 처리된
     * 이벤트는 order.create의 기존 멱등 처리(evt:processed:*)가 재시도에서도 중복 반영을 막아준다.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template);
        FixedBackOff backOff = new FixedBackOff(1000L, 3L); // 1초 간격, 최대 3회 재시도
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(NotFoundException.class, IllegalArgumentException.class);
        return handler;
    }
}