package com.sparta.product.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
     *  락 획득 (Lua 스크립트 적용)
     */
    public boolean acquireLock(String key, String requestId, long expireTimeMillis) {
        Long result = redisTemplate.execute(lockScript, Collections.singletonList(key), requestId, String.valueOf(expireTimeMillis));

        if (result != null && result == 1) {
            log.info(" Redis Lock 획득 성공 - Key: {}, RequestID: {}, ExpireTime: {}ms", key, requestId, expireTimeMillis);
            return true;
        } else {
            log.warn(" Redis Lock 획득 실패 - Key: {}, RequestID: {}", key, requestId);
            return false;
        }
    }

    /**
     *  락 해제 (Lua 스크립트 적용)
     */
    public boolean releaseLock(String key, String requestId) {
        // Lua 스크립트를 사용하여 락을 안전하게 해제
        Long result = redisTemplate.execute(unlockScript, Collections.singletonList(key), requestId);

        // 결과가 1이면 락 해제 성공, 0이면 실패
        return result != null && result == 1;
    }

    /**
     *  TTL 없이 Redis 캐시에 저장 (객체 -> JSON 변환)
     */
    public void saveToCache(String key, Object value) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value); // 객체 -> JSON 문자열 변환
            redisTemplate.opsForValue().set(key, jsonValue);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 저장 오류: JSON 변환 실패", e);
        }
    }

    /**
     *  TTL 설정 가능 (객체 -> JSON 변환)
     */
    public void saveToCache(String key, Object value, long ttlInSeconds) {
        try {
            String jsonValue = objectMapper.writeValueAsString(value); // 객체 -> JSON 변환
            redisTemplate.opsForValue().set(key, jsonValue, ttlInSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 저장 오류: JSON 변환 실패", e);
        }
    }

    /**
     *  캐시에서 값 조회 (JSON 문자열 -> 객체 변환)
     */
    public <T> T getFromCache(String key, Class<T> type) {
        String jsonValue = redisTemplate.opsForValue().get(key);
        if (jsonValue == null) return null;

        try {
            return objectMapper.readValue(jsonValue, type); // JSON 문자열 -> 객체 변환
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Redis 조회 오류: JSON 변환 실패", e);
        }
    }

    /**
     *  Redis 캐시 삭제
     */
    public void deleteFromCache(String key) {
        redisTemplate.delete(key);
    }
    public boolean acquireLockWithRetry(String lockKey, String requestId, long expireTimeMillis, int maxRetry) {
        int retryCount = 0;
        long retryDelay = 50;

        while (retryCount < maxRetry) {
            boolean locked = acquireLock(lockKey, requestId, expireTimeMillis);
            if (locked) {
                return true;
            }

            retryCount++;
            log.warn(" 락 재시도 - Key: {}, 현재 재시도 횟수: {}", lockKey, retryCount);

            try {
                Thread.sleep(retryDelay);
                retryDelay = Math.min(retryDelay * 2, 500); // 최대 500ms
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        log.error(" 락 획득 실패 - Key: {}, 최대 재시도 횟수 초과", lockKey);
        return false;
    }
}