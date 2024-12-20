package com.sparta.haengye_project.user.service;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TokenService {

    private RedisTemplate<String, String> redisTemplate;

    public TokenService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    // 이메일 인증 토큰 생성 후 Redis에 저장
    public void createEmailVerificationToken(String email, String token) {
        // 토큰을 Redis에 저장하고, 만료 시간을 설정 (예: 24시간)
        redisTemplate.opsForValue().set(email, token, 24, TimeUnit.HOURS);
    }

    // 이메일 인증 토큰 검증
    public boolean verifyEmailToken(String email, String token) {
        String storedToken = redisTemplate.opsForValue().get(email);

        if (storedToken != null && storedToken.equals(token)) {
            // 토큰이 일치하면 인증 완료 처리 (레디스에서 삭제하거나 만료)
            redisTemplate.delete(email);  // 토큰 사용 후 삭제
            return true;
        }
        return false;
    }
    // JWT 토큰을 블랙리스트에 추가
    public void addToBlacklist(String token) {
        // JWT 토큰을 Redis에 저장하고, 만료 시간을 설정 (예: 24시간)
        redisTemplate.opsForValue().set(token, "BLACKLISTED", 24, TimeUnit.HOURS);
    }
    // 블랙리스트에 있는 토큰인지 확인
    public boolean isTokenBlacklisted(String token) {
        return redisTemplate.hasKey(token);
    }
}
