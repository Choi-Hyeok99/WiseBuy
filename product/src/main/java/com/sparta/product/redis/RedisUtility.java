package com.sparta.product.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * product 서비스의 Redis 접근을 모아둔 유틸.
 *
 * 용도 세 가지:
 *  1) 재고 캐시           : {@code product_stock:{id}} 키에 재고를 JSON으로 저장/조회
 *  2) 원자적 재고 증감      : Lua 스크립트로 "조회 → 비교 → 차감/복구"를 단일 원자 연산으로 실행
 *  3) 이벤트 멱등 처리      : Kafka at-least-once 재전달로 같은 이벤트가 또 와도 중복 처리하지 않도록
 */
@Component
public class RedisUtility {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisUtility(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ---- 1) 재고 캐시 -------------------------------------------------------

    public void saveToCache(String key, Object value) {
        redisTemplate.opsForValue().set(key, toJson(value));
    }

    public void saveToCache(String key, Object value, long ttlInSeconds) {
        redisTemplate.opsForValue().set(key, toJson(value), ttlInSeconds, TimeUnit.SECONDS);
    }

    public <T> T getFromCache(String key, Class<T> type) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 조회 오류: JSON 변환 실패", e);
        }
    }

    public void deleteFromCache(String key) {
        redisTemplate.delete(key);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 저장 오류: JSON 변환 실패", e);
        }
    }

    // ---- 2) 원자적 재고 증감 (Lua) ---------------------------------------------

    // 재고 차감: 없으면 -1, 부족하면 -2, 성공하면 남은 재고. get→비교→decrby가 Redis 단일 스레드로 한 덩어리로 실행됨.
    private static final DefaultRedisScript<Long> STOCK_UPDATE_SCRIPT = new DefaultRedisScript<>(
            "local stock = redis.call('get', KEYS[1]) " +
            "if not stock then return -1 end " +
            "stock = tonumber(stock) " +
            "if stock < tonumber(ARGV[1]) then return -2 end " +
            "redis.call('decrby', KEYS[1], ARGV[1]) " +
            "return stock - ARGV[1]", Long.class);

    // 재고 복구: 없으면 -1, 성공하면 복구 후 재고.
    private static final DefaultRedisScript<Long> STOCK_ROLLBACK_SCRIPT = new DefaultRedisScript<>(
            "local stock = redis.call('get', KEYS[1]) " +
            "if not stock then return -1 end " +
            "redis.call('incrby', KEYS[1], ARGV[1]) " +
            "return tonumber(stock) + ARGV[1]", Long.class);

    public Long updateStockInRedis(String productId, int quantity) {
        return redisTemplate.execute(STOCK_UPDATE_SCRIPT,
                Collections.singletonList("product_stock:" + productId), String.valueOf(quantity));
    }

    public Long rollbackStockInRedis(String productId, int quantity) {
        return redisTemplate.execute(STOCK_ROLLBACK_SCRIPT,
                Collections.singletonList("product_stock:" + productId), String.valueOf(quantity));
    }

    // ---- 3) 이벤트 멱등 처리 --------------------------------------------------

    // 처리 성공 후 markEventProcessed로 표시하고, 같은 이벤트가 또 오면 isEventProcessed로 걸러낸다.
    // TTL 1시간 — 그 이후까지 재전달되는 경우는 사실상 없고, 키가 무한정 쌓이지 않게 한다.
    private static final String EVENT_KEY_PREFIX = "evt:processed:";

    public boolean isEventProcessed(String eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(EVENT_KEY_PREFIX + eventId));
    }

    public void markEventProcessed(String eventId) {
        redisTemplate.opsForValue().set(EVENT_KEY_PREFIX + eventId, "1", Duration.ofHours(1));
    }
}
