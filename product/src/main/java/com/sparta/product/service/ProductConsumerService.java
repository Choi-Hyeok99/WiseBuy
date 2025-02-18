package com.sparta.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.common.dto.KafkaMessage;
import com.sparta.product.dto.StockUpdateRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductConsumerService {

    @KafkaListener(topics = "order.create", groupId = "product-service")
    public void consumerOrderEvent(@Payload KafkaMessage<Map<String, Object>> message) {
        log.info("📩 Kafka 메시지 수신: {}", message);

        try {
            // message.getData()에서 Map을 받아서 StockUpdateRequestDto로 변환
            Map<String, Object> dataMap = message.getData();
            ObjectMapper objectMapper = new ObjectMapper();
            StockUpdateRequestDto stockUpdateDto = objectMapper.convertValue(dataMap, StockUpdateRequestDto.class);

            Long productId = stockUpdateDto.getProductId();
            int quantity = stockUpdateDto.getQuantity();

            log.info("📦 주문된 상품 ID: {}, 감소할 수량: {}", productId, quantity);
        } catch (Exception e) {
            log.error("❌ Kafka 메시지 처리 중 오류 발생: ", e);
        }
    }
}