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
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.apache.kafka.clients.admin.NewTopic;
import com.sparta.common.dto.KafkaMessage;

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
    public ConcurrentKafkaListenerContainerFactory<String, KafkaMessage<Map<String, Object>>> batchFactory() {
        ConcurrentKafkaListenerContainerFactory<String, KafkaMessage<Map<String, Object>>> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(batchConsumerFactory());
        factory.setBatchListener(true);   // 배치 모드
        factory.setConcurrency(3);        // 파티션 3개를 컨슈머 스레드 3개가 병렬 소비
        return factory;
    }
}