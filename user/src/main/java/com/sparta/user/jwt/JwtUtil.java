package com.sparta.user.jwt;


import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final Key SECRET_KEY;

    public JwtUtil(@Value("${jwt.secret}") String jwtSecret) {
        log.info("Loaded JWT_SECRET from .env: {}", jwtSecret); // 디버깅 로그 추가

        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalArgumentException("JWT_SECRET is not set or is empty");
        }

        log.info("Loaded JWT_SECRET: {}", jwtSecret);

        try {
            // 일반 문자열로 Key 생성
            this.SECRET_KEY = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
        } catch (Exception e) {
            log.error("Error creating SECRET_KEY from string", e);
            throw e;
        }

        log.info("Generated SECRET_KEY successfully");
    }

    public String generateToken(String email,Long id,String address) {
        return Jwts.builder()
                   .setHeaderParam("typ", "JWT")
                   .setHeaderParam("alg", "HS256")
                   .setSubject(String.valueOf(id))
                    .claim("email" , email)
                   .claim("address", address)     // 주소 추가
                   .setIssuedAt(new Date())
                   .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)) // 24시간 유효
                   .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
                   .compact();
    }

    public String getUserEmailFromToken(String token) {
        try {
            String email = Jwts.parserBuilder()
                               .setSigningKey(SECRET_KEY)
                               .build()
                               .parseClaimsJws(token)
                               .getBody()
                               .getSubject();
            log.info("Extracted email from token: {}", email);
            return email;
        } catch (Exception e) {
            log.error("Failed to extract email from token", e);
            throw e;
        }
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(SECRET_KEY).build().parseClaimsJws(token);
            log.info("Token is valid");
            return true;
        } catch (Exception e) {
            log.error("Invalid token", e);
            return false;
        }
    }
}