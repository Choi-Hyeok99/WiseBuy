package com.sparta.product.service;

import com.sparta.common.dto.KafkaMessage;
import com.sparta.product.exception.NotFoundException;
import com.sparta.product.redis.RedisUtility;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductConsumerService {

    private final ProductService productService;
    private final RedisUtility redisUtility;

    /**
     * 주문 생성 이벤트를 배치로 받아 DB 재고를 반영한다.
     *
     * 재고 예약(오버셀 방지)은 주문 시점에 product 서비스가 Redis에서 원자적으로 이미 끝냈고,
     * 여기서는 그 결과를 DB에 뒤늦게 반영하는 역할만 한다. 한 번에 여러 건을 받아
     * "상품별로 수량을 합산 → 상품당 UPDATE 1회"로 처리해 DB 쓰기 횟수를 줄인다.
     *
     * payload의 quantity 규약: 양수 = 주문(차감), 음수 = 취소(복구).
     *
     * (예전엔 이 컨슈머가 payload에 없는 productId/quantity를 꺼내 쓰느라 매번 아무 일도 안 했고,
     *  실제 DB 반영은 별도 stock.update 컨슈머가 맡았는데 그쪽은 배치/단건 설정이 어긋나 동작하지 않았다.
     *  두 경로를 이 배치 컨슈머 하나로 합쳤다.)
     */
    @KafkaListener(topics = "order.create", groupId = "product-service", containerFactory = "batchFactory")
    public void consumeOrderEvents(@Payload List<KafkaMessage<Map<String, Object>>> messages) {
        log.info("order.create 배치 수신: {}건", messages.size());

        Map<Long, Integer> deltaByProduct = new HashMap<>();
        for (KafkaMessage<Map<String, Object>> message : messages) {
            try {
                Map<String, Object> data = message.getData();
                Long productId = ((Number) data.get("productId")).longValue();
                int quantity = ((Number) data.get("quantity")).intValue();
                deltaByProduct.merge(productId, quantity, Integer::sum);
            } catch (Exception e) {
                log.error("order.create 메시지 파싱 실패: {}", message, e);
            }
        }

        deltaByProduct.forEach((productId, delta) -> {
            try {
                productService.executeStockUpdate(productId, delta);
            } catch (Exception e) {
                log.error("DB 재고 반영 실패 - 상품 ID: {}, 합산 수량: {}", productId, delta, e);
            }
        });
    }

    /**
     * 결제 실패 시(SAGA 보상) 재고 복구. Redis에서 원자적으로 되돌린다.
     */
    @KafkaListener(topics = "stock.rollback", groupId = "product-service")
    @Transactional
    public void rollbackOrderEvent(@Payload KafkaMessage<Map<String, Object>> message) {
        log.info("stock.rollback 수신 {}", message);

        try {
            Map<String, Object> data = message.getData();
            Long productId = ((Number) data.get("productId")).longValue();
            int quantity = ((Number) data.get("quantity")).intValue();

            Long rolledBack = redisUtility.rollbackStockInRedis(String.valueOf(productId), quantity);
            if (rolledBack == -1) {
                throw new NotFoundException("상품이 존재하지 않습니다.");
            }
            // DB도 같이 되돌린다 (executeStockUpdate는 양수=차감이므로 복구는 음수를 넘긴다)
            productService.executeStockUpdate(productId, -quantity);
            log.info("재고 복구 완료 - 상품 ID: {}, 복원 후 Redis 재고: {}", productId, rolledBack);
        } catch (Exception e) {
            log.error("stock.rollback 처리 중 오류", e);
        }
    }
}
