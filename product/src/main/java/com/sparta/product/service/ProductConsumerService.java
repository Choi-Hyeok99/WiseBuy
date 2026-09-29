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

import java.util.ArrayList;
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
        Map<Long, List<String>> eventIdsByProduct = new HashMap<>();
        for (KafkaMessage<Map<String, Object>> message : messages) {
            try {
                Map<String, Object> data = message.getData();
                Long orderId = ((Number) data.get("orderId")).longValue();
                Long productId = ((Number) data.get("productId")).longValue();
                int quantity = ((Number) data.get("quantity")).intValue();
                String eventId = orderId + ":" + productId;

                // 멱등 처리: 재전달로 같은 (주문,상품) 이벤트가 또 와도 DB 재고를 두 번 반영하지 않는다.
                if (redisUtility.isEventProcessed(eventId)) {
                    log.info("이미 반영된 order.create 이벤트 - {} (건너뜀)", eventId);
                    continue;
                }
                deltaByProduct.merge(productId, quantity, Integer::sum);
                eventIdsByProduct.computeIfAbsent(productId, k -> new ArrayList<>()).add(eventId);
            } catch (Exception e) {
                log.error("order.create 메시지 파싱 실패: {}", message, e);
            }
        }

        deltaByProduct.forEach((productId, delta) -> {
            try {
                productService.executeStockUpdate(productId, delta);
                // DB 반영 성공 후에 "처리됨"으로 표시한다. 실패하면 표시 안 하므로 재전달 시 다시 시도됨.
                eventIdsByProduct.get(productId).forEach(redisUtility::markEventProcessed);
            } catch (NotFoundException e) {
                // 상품 자체가 없으면 재시도해도 결과가 같다 - 비즈니스 예외, 로그만 남기고 건너뜀(재시도 대상 아님)
                log.warn("DB 재고 반영 대상 상품 없음 - 상품 ID: {} (재시도 무의미, 건너뜀)", productId, e);
            } catch (Exception e) {
                // 그 외(DB 연결 등 인프라성)는 배치 전체를 실패시켜 컨테이너 레벨 재시도로 넘긴다.
                // 이미 처리된 이벤트는 markEventProcessed로 표시돼 있어 재시도해도 다시 반영되지 않는다.
                log.error("DB 재고 반영 실패(재시도 대상) - 상품 ID: {}, 합산 수량: {}", productId, delta, e);
                throw new RuntimeException("order.create DB 반영 실패 - 상품 ID: " + productId, e);
            }
        });
    }

    /**
     * 결제 실패 시(SAGA 보상) 재고 복구. Redis에서 원자적으로 되돌리고 DB도 맞춘다.
     *
     * 멱등 처리를 "Redis 복구"와 "DB 반영" 두 단계로 나눠서 추적한다(evt:processed:STOCK_ROLLBACK:{orderId}:{productId}):
     *   null(상태 없음)  → 처음 처리: Redis 복구 → REDIS_DONE 마킹 → DB 반영 → DONE 마킹
     *   REDIS_DONE      → Redis 복구는 이미 끝났음. 재시도 시 Redis는 건드리지 않고 DB만 이어서 시도한다.
     *   DONE            → 완전히 끝남. 통째로 건너뛴다.
     * 이렇게 해야 "Redis 복구는 성공했는데 DB 반영만 실패 → 컨테이너가 재시도"하는 상황에서
     * Redis가 중복으로 복구되는 것을 막으면서도 DB는 결국 정상 반영된다.
     */
    @KafkaListener(topics = "stock.rollback", groupId = "product-service")
    @Transactional
    public void rollbackOrderEvent(@Payload KafkaMessage<Map<String, Object>> message) {
        log.info("stock.rollback 수신 {}", message);

        Map<String, Object> data = message.getData();
        Long orderId = ((Number) data.get("orderId")).longValue();
        Long productId = ((Number) data.get("productId")).longValue();
        int quantity = ((Number) data.get("quantity")).intValue();
        String eventId = "STOCK_ROLLBACK:" + orderId + ":" + productId;

        String state = redisUtility.getEventState(eventId);
        if ("DONE".equals(state)) {
            log.info("이미 완전히 처리된 stock.rollback 이벤트 - {} (건너뜀)", eventId);
            return;
        }

        if (!"REDIS_DONE".equals(state)) {
            // 처음 처리하는 경우에만 Redis를 건드린다. 재시도(REDIS_DONE)면 이 블록을 건너뛴다.
            Long rolledBack = redisUtility.rollbackStockInRedis(String.valueOf(productId), quantity);
            if (rolledBack == -1) {
                // 상품 자체가 없으면 재시도해도 결과가 같다 - 비즈니스 예외, 완료로 표시하고 종료
                log.warn("stock.rollback 대상 상품 없음 - 상품 ID: {} (재시도 무의미, 건너뜀)", productId);
                redisUtility.markEventState(eventId, "DONE");
                return;
            }
            redisUtility.markEventState(eventId, "REDIS_DONE");
            log.info("stock.rollback Redis 복구 완료 - 상품 ID: {}, 복원 후 Redis 재고: {}", productId, rolledBack);
        } else {
            log.info("stock.rollback Redis 단계는 이미 완료됨 - DB 반영만 재시도 - {}", eventId);
        }

        try {
            // DB도 같이 되돌린다 (executeStockUpdate는 양수=차감이므로 복구는 음수를 넘긴다)
            productService.executeStockUpdate(productId, -quantity);
        } catch (NotFoundException e) {
            log.warn("stock.rollback DB 반영 대상 상품 없음 - 상품 ID: {} (재시도 무의미, 건너뜀)", productId, e);
            redisUtility.markEventState(eventId, "DONE");
            return;
        }

        redisUtility.markEventState(eventId, "DONE");
        log.info("stock.rollback 처리 완료 - 상품 ID: {}", productId);
        // 그 외 예외(DB 연결 등 인프라성)는 executeStockUpdate에서 그대로 전파되며, 여기서 잡지 않는다.
        // 상태가 REDIS_DONE으로 남아있으므로 컨테이너가 재시도할 때 Redis는 건드리지 않고 DB만 다시 시도한다.
    }
}
