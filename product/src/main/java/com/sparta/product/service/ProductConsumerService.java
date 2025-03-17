package com.sparta.product.service;

import com.sparta.common.dto.KafkaMessage;
import com.sparta.product.dto.StockUpdateRequestDto;
import com.sparta.product.entitiy.Product;
import com.sparta.product.exception.NotFoundException;
import com.sparta.product.redis.RedisUtility;
import com.sparta.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductConsumerService {

    private final ProductRepository productRepository;
    private final RedisUtility redisUtility;

    private static final String STOCK_KEY_PREFIX = "product_stock:";

    @KafkaListener(topics = "order.create", groupId = "product-service", containerFactory = "batchFactory")
    public void consumeOrderEvents(@Payload List<KafkaMessage<Map<String, Object>>> messages) {
        log.info("Batch 메시지 수신: {}개", messages.size());

        messages.forEach(message -> {
            try {
                Long productId = ((Number) message.getData().getOrDefault("productId", 0)).longValue();
                int quantity = ((Number) message.getData().getOrDefault("quantity", 0)).intValue();

                if (productId > 0 && quantity > 0) {
                    updateStockAsync(productId, quantity);
                }
            } catch (Exception e) {
                log.error("Kafka 메시지 처리 중 오류 발생", e);
            }
        });
    }
    /**
     * 비동기 재고 감소 (Redis 캐시 기반)
     */
    private void updateStockAsync(Long productId, int quantity) {
        CompletableFuture.runAsync(() -> {
            String stockKey = STOCK_KEY_PREFIX + productId;
            Integer currentStock = redisUtility.getFromCache(stockKey, Integer.class);

            if (currentStock == null) {
                log.warn("Redis 캐시에 해당 상품이 없음. DB에서 조회");
                currentStock = productRepository.findStockById(productId)
                                                .orElseThrow(() -> new IllegalArgumentException("상품이 존재하지 않습니다."));
            }

            int updatedStock = currentStock - quantity;
            if (updatedStock < 0) {
                log.warn("재고 부족 - 요청 취소 (상품 ID: {}, 현재 재고: {}, 요청 수량: {})",
                        productId, currentStock, quantity);
                return;
            }

            // Redis 및 DB 동시 업데이트
            redisUtility.saveToCache(stockKey, updatedStock);
            productRepository.updateStock(productId, updatedStock);

            log.info("비동기 재고 감소 완료 - 상품 ID: {}, 남은 재고: {}", productId, updatedStock);
        });
    }

    @KafkaListener(topics = "stock.rollback", groupId = "product-service")
    @Transactional
    public void rollbackOrderEvent(@Payload KafkaMessage<Map<String, Object>> message) {
        log.info("Kafka 재고 롤백 이벤트 수신 {}", message);

        try {
            Map<String, Object> datamap = message.getData();
            Long productId = ((Number) datamap.get("productId")).longValue();
            int quantity = (int) datamap.get("quantity");

            // Redis에서 원자적으로 재고 복구 수행
            Long rolledBackStock = redisUtility.rollbackStockInRedis(String.valueOf(productId), quantity);

            if (rolledBackStock == -1) {
                throw new NotFoundException("상품이 존재하지 않습니다.");
            }

            log.info("재고 복구 완료 - 상품 ID: {}, 복원된 재고: {}", productId, rolledBackStock);
        } catch (Exception e) {
            log.error("Kafka 재고 롤백 이벤트 처리 중 오류 발생 : ", e);
        }
    }
    @KafkaListener(topics = "stock.update", groupId = "product-service", containerFactory = "kafkaListenerContainerFactory")
    @Transactional
    public void consumeStockUpdate(@Payload StockUpdateRequestDto message) {
        Long productId = message.getProductId();
        int quantity = message.getQuantity();

        Product product = productRepository.findById(productId)
                                           .orElseThrow(() -> new NotFoundException("상품이 존재하지 않습니다."));

        product.setStock(product.getStock() - quantity);
        productRepository.save(product);

        log.info("DB에 비동기적으로 재고 반영 완료 - 상품 ID: {}, 차감된 수량: {}", productId, quantity);
    }
}