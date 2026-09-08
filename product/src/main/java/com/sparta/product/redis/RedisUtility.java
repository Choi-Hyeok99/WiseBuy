package com.sparta.product.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RedisUtility {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper; // JSON 변환을 위한 ObjectMapper 추가

    public RedisUtility(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private static final String LOCK_SCRIPT =
            "if redis.call('set', KEYS[1], ARGV[1], 'NX', 'PX', ARGV[2]) then " +
                    "    return 1 " +
                    "else " +
                    "    return 0 " +
                    "end";

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "    return redis.call('del', KEYS[1]) " +
                    "else " +
                    "    return 0 " +
                    "end";

    private static final String STOCK_UPDATE_SCRIPT =
            "local stock = redis.call('get', KEYS[1]) " +
                    "if not stock then " +
                    "   return -1 " +
                    "end " +
                    "stock = tonumber(stock) " +
                    "if stock < tonumber(ARGV[1]) then " +
                    "   return -2 " +
                    "end " +
                    "redis.call('decrby', KEYS[1], ARGV[1]) " +
                    "return stock - ARGV[1]";

    private static final String STOCK_ROLLBACK_SCRIPT =
            "local stock = redis.call('get', KEYS[1]) " +
                    "if not stock then return -1 end " +
                    "stock = tonumber(stock) " +
                    "redis.call('incrby', KEYS[1], ARGV[1]) " +
                    "return stock + ARGV[1]";


    private static final DefaultRedisScript<Long> lockScript = new DefaultRedisScript<>(LOCK_SCRIPT, Long.class);
    private static final DefaultRedisScript<Long> unlockScript = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
    private static final DefaultRedisScript<Long> stockUpdateScript = new DefaultRedisScript<>(STOCK_UPDATE_SCRIPT, Long.class);
    private static final DefaultRedisScript<Long> stockRollbackScript = new DefaultRedisScript<>(STOCK_ROLLBACK_SCRIPT, Long.class);


    public boolean acquireLock(String key, String requestId, long expireTimeMillis) {
        String lockKey = "lock:" + key; // 락 키 세분화
        Long result = redisTemplate.execute(lockScript, Collections.singletonList(lockKey), requestId, String.valueOf(expireTimeMillis));

        return result != null && result == 1;
    }
    public boolean releaseLock(String key, String requestId) {
        Long result = redisTemplate.execute(unlockScript, Collections.singletonList(key), requestId);

        return result != null && result == 1;
    }
    public void saveToCache(String key, Object value) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue()
                         .set(key, jsonValue);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 저장 오류: JSON 변환 실패", e);
        }
    }
    public void saveToCache(String key, Object value, long ttlInSeconds) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue()
                         .set(key, jsonValue, ttlInSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 저장 오류: JSON 변환 실패", e);
        }
    }
    public <T> T getFromCache(String key, Class<T> type) {
        String jsonValue = redisTemplate.opsForValue()
                                        .get(key);
        if (jsonValue == null) return null;

        try {
            return objectMapper.readValue(jsonValue, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 조회 오류: JSON 변환 실패", e);
        }
    }
    public void deleteFromCache(String key) {
        redisTemplate.delete(key);
    }
    @Retry(name = "redis-lock-retry", fallbackMethod = "fallbackAcquireLock")
    public boolean acquireLockWithRetry(String lockKey, String requestId, long expireTimeMillis) {
        boolean locked = acquireLock(lockKey, requestId, expireTimeMillis);
        if (!locked) {
            throw new IllegalStateException("Redis Lock 획득 실패: " + lockKey);
        }
        return true;
    }
    public boolean fallbackAcquireLock(String lockKey, String requestId, long expireTimeMillis, Exception ex) {
        log.error("Redis Lock 획득 실패: {}, RequestID: {} - 예외 발생: {}", lockKey, requestId, ex.getMessage());
        return false;
    }
    public Long updateStockInRedis(String productId, int quantity) {
        String stockKey = "product_stock:" + productId;
        return redisTemplate.execute(stockUpdateScript, Collections.singletonList(stockKey), String.valueOf(quantity));
    }
    public Long rollbackStockInRedis(String productId, int quantity) {
        String stockKey = "product_stock:" + productId;
        return redisTemplate.execute(stockRollbackScript, Collections.singletonList(stockKey), String.valueOf(quantity));
    }

    // Kafka at-least-once 재전달 대비 이벤트 멱등 처리.
    // 처리 성공 후 markEventProcessed로 표시하고, 다음에 같은 이벤트가 오면 isEventProcessed로 걸러낸다.
    // TTL 1시간 — 그 이후까지 재전달되는 경우는 사실상 없고, 키가 무한정 쌓이지 않게 한다.
    private static final String EVENT_KEY_PREFIX = "evt:processed:";

    public boolean isEventProcessed(String eventId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(EVENT_KEY_PREFIX + eventId));
    }

    public void markEventProcessed(String eventId) {
        redisTemplate.opsForValue().set(EVENT_KEY_PREFIX + eventId, "1", java.time.Duration.ofHours(1));
    }
}