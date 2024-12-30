//package com.sparta.haengye_project.user.jwt;
//
//import io.github.cdimascio.dotenv.Dotenv;
//import io.jsonwebtoken.Jwts;
//import io.jsonwebtoken.SignatureAlgorithm;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.stereotype.Component;
//
//import javax.crypto.spec.SecretKeySpec;
//import java.nio.charset.StandardCharsets;
//import java.security.Key;
//import java.util.Date;
//
//@Slf4j
//@Component
//public class JwtUtil {
//
//    private static final Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load(); // .env 파일 로드
//    private final String SECRET_KEY_STRING = dotenv.get("JWT_SECRET", System.getenv("JWT_SECRET"));
//    private final Key SECRET_KEY;
//
//    public JwtUtil() {
//        if (SECRET_KEY_STRING == null || SECRET_KEY_STRING.trim().isEmpty()) {
//            throw new IllegalArgumentException("JWT_SECRET is not set or is empty");
//        }
//
//        log.info("Loaded JWT_SECRET: {}", SECRET_KEY_STRING);
//
//        try {
//            // 일반 문자열로 Key 생성
//            this.SECRET_KEY = new SecretKeySpec(SECRET_KEY_STRING.getBytes(StandardCharsets.UTF_8), SignatureAlgorithm.HS256.getJcaName());
//        } catch (Exception e) {
//            log.error("Error creating SECRET_KEY from string", e);
//            throw e;
//        }
//
//        log.info("Generated SECRET_KEY successfully");
//    }
//
//    public String generateToken(String email) {
//        return Jwts.builder()
//                   .setHeaderParam("typ", "JWT")
//                   .setHeaderParam("alg", "HS256")
//                   .setSubject(email)
//                   .setIssuedAt(new Date())
//                   .setExpiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 24)) // 24시간 유효
//                   .signWith(SECRET_KEY, SignatureAlgorithm.HS256)
//                   .compact();
//    }
//
//    public String getUserEmailFromToken(String token) {
//        try {
//            String email = Jwts.parserBuilder()
//                               .setSigningKey(SECRET_KEY)
//                               .build()
//                               .parseClaimsJws(token)
//                               .getBody()
//                               .getSubject();
//            log.info("Extracted email from token: {}", email);
//            return email;
//        } catch (Exception e) {
//            log.error("Failed to extract email from token", e);
//            throw e;
//        }
//    }
//
//    public boolean validateToken(String token) {
//        try {
//            Jwts.parserBuilder().setSigningKey(SECRET_KEY).build().parseClaimsJws(token);
//            log.info("Token is valid");
//            return true;
//        } catch (Exception e) {
//            log.error("Invalid token", e);
//            return false;
//        }
//    }
//}