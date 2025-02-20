package com.sparta.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparta.common.dto.KafkaMessage;
import com.sparta.product.dto.StockUpdateRequestDto;
import com.sparta.product.entitiy.Product;
import com.sparta.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductConsumerService {

    private final ProductRepository productRepository;

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

    @KafkaListener(topics = "stock.rollback", groupId = "product-service")
    @Transactional
    public void rollbackOrderEvent(@Payload KafkaMessage<Map<String, Object>> message){
        log.info("Kafka 재고 롤백 이벤트 수신 {}", message);

        try{
            Map<String, Object> datamap = message.getData();
            Long productId = ((Number) datamap.get("productId")).longValue();
            int quantity = (int) datamap.get("quantity");

            Product product = productRepository.findById(productId)
                                               .orElseThrow(()-> new IllegalArgumentException("상품을 찾을 수 없습니다."));

            // 주문이 취소되었으므로 재고 복구
            product.setStock(product.getStock() + quantity);
            productRepository.save(product);
            log.info("재고 복구 완료 : 상품 ID {} -> {}개 증가", productId, quantity);

        } catch (Exception e){
            log.error("Kafka 재고 롤백 이벤트 처리 중 오류 발생 : ",e);
        }
    }
}