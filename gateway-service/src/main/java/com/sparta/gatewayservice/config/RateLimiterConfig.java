package com.sparta.gatewayservice.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * RequestRateLimiter가 "누구 기준으로 한도를 세는지" 정하는 키.
 *
 * 로그인 요청(JwtFilter가 X-Claim-sub를 심어줌)은 사용자별로, 그렇지 않은 요청(재고 조회 등)은
 * 호출한 IP별로 버킷을 나눈다. 한 사용자가 선착순에 스크립트로 몰아쳐도 그 사용자 버킷만
 * 바닥나고 다른 사용자는 영향을 안 받게 하는 게 목적.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    @Primary
    public KeyResolver userOrIpKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst("X-Claim-sub");
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}
