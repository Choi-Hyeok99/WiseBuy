package com.sparta.user.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
public class RedisRepository {

    private final StringRedisTemplate redisTemplate;

    public RedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // 인증 코드 저장
    public void setDataExpire(String key, String value, long timeout) {
        redisTemplate.opsForValue().set(key, value, timeout, TimeUnit.SECONDS); // 5분(300초) 동안 저장
    }

    // 인증 코드 가져오기
    public String getData(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    // 데이터 삭제
    public void deleteData(String key) {
        redisTemplate.delete(key);
    }

    // 데이터가 존재하는지 확인
    public boolean existData(String key) {
        return redisTemplate.hasKey(key);
    }
}
