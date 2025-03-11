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

    // Redis 분산락 Lua 스크립트
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

    private static final DefaultRedisScript<Long> lockScript = new DefaultRedisScript<>(LOCK_SCRIPT, Long.class);
    private static final DefaultRedisScript<Long> unlockScript = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);

    /**
     * 락 해제 (Lua 스크립트 적용)
     */
    public boolean releaseLock(String key, String requestId) {
        String currentLockOwner = redisTemplate.opsForValue()
                                               .get(key);
        if (!requestId.equals(currentLockOwner)) {
            log.warn("다른 요청이 락을 가지고 있음 - Key: {}, 기존 Owner: {}, 요청 Owner: {}", key, currentLockOwner, requestId);
            return false;
        }

        Long result = redisTemplate.execute(unlockScript, Collections.singletonList(key), requestId);
        boolean success = result != null && result == 1;

        if (success) {
            log.info("Redis Lock 해제 성공 - Key: {}, RequestID: {}", key, requestId);
        } else {
            log.warn("Redis Lock 해제 실패 - Key: {}, RequestID: {}", key, requestId);
        }
        return success;
    }

    /**
     * TTL 없이 Redis 캐시에 저장 (객체 -> JSON 변환)
     */
    public void saveToCache(String key, Object value) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value); // 객체 -> JSON 문자열 변환
            redisTemplate.opsForValue()
                         .set(key, jsonValue);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 저장 오류: JSON 변환 실패", e);
        }
    }

    /**
     * TTL 설정 가능 (객체 -> JSON 변환)
     */
    public void saveToCache(String key, Object value, long ttlInSeconds) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value); // 객체 -> JSON 변환
            redisTemplate.opsForValue()
                         .set(key, jsonValue, ttlInSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 저장 오류: JSON 변환 실패", e);
        }
    }

    /**
     * 캐시에서 값 조회 (JSON 문자열 -> 객체 변환)
     */
    public <T> T getFromCache(String key, Class<T> type) {
        String jsonValue = redisTemplate.opsForValue()
                                        .get(key);
        if (jsonValue == null) return null;

        try {
            return objectMapper.readValue(jsonValue, type); // JSON 문자열 -> 객체 변환
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 조회 오류: JSON 변환 실패", e);
        }
    }

    /**
     * Redis 캐시 삭제
     */
    public void deleteFromCache(String key) {
        redisTemplate.delete(key);
    }

    // Resilience4j Retry 적용 (자동 재시도 기능 추가)
    @Retry(name = "redis-lock-retry", fallbackMethod = "fallbackAcquireLock")
    public boolean acquireLockWithRetry(String lockKey, String requestId, long expireTimeMillis) {
        boolean locked = acquireLock(lockKey, requestId, expireTimeMillis);
        if (!locked) {
            throw new IllegalStateException("Redis Lock 획득 실패: " + lockKey);
        }
        return true;
    }

    // Redis 락 획득 실패 시 실행할 Fallback 메서드
    public boolean fallbackAcquireLock(String lockKey, String requestId, long expireTimeMillis, Exception ex) {
        log.error("Redis Lock 획득 실패: {}, RequestID: {} - 예외 발생: {}", lockKey, requestId, ex.getMessage());
        return false;
    }


    // 기존 락 획득 메서드 (Lua 기반 적용)
    public boolean acquireLock(String key, String requestId, long expireTimeMillis) {
        Long result = redisTemplate.execute(
                new DefaultRedisScript<>(LOCK_SCRIPT, Long.class),
                Collections.singletonList(key), requestId, String.valueOf(expireTimeMillis));

        if (result != null && result == 1) {
            log.info("Redis Lock 획득 성공 - Key: {}, RequestID: {}, ExpireTime: {}ms", key, requestId, expireTimeMillis);
            return true;
        } else {
            log.warn("Redis Lock 획득 실패 - Key: {}, RequestID: {}", key, requestId);
            return false;
        }
    }
}