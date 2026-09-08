package com.sparta.gatewayservice.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@Slf4j
public class JwtFilter extends AbstractGatewayFilterFactory<JwtFilter.Config> {

    @Value("${jwt.secret}")
    private String jwtSecret;

    public JwtFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {

        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();
            String path = request.getURI().getPath();
            // 요청마다 찍히던 로그들. 부하 중 게이트웨이 CPU를 크게 먹어서 info → debug로 내렸다.
            // Token 값 로그는 JWT가 그대로 로그에 남아 보안상으로도 안 좋아 debug로.
            log.debug("path: {}", path);

            if (!request.getHeaders().containsKey(HttpHeaders.AUTHORIZATION)) {
                return chain.filter(exchange);
            }

            String authHeader = request.getHeaders().getOrEmpty(HttpHeaders.AUTHORIZATION).get(0);
            if (!authHeader.startsWith("Bearer ")) {
                return handleUnauthorized(response, "Invalid Authorization header format.");
            }

            String token = authHeader.substring(7);
            log.debug("Token: {}", token);

            try {
                Claims claims = Jwts.parser()
                                    .setSigningKey(jwtSecret.getBytes(StandardCharsets.UTF_8))
                                    .parseClaimsJws(token)
                                    .getBody();

                log.debug("JWT Claims: ");
                ServerHttpRequest.Builder mutatedRequest = request.mutate();

                // ** 역할(Role) 검증 제거 **
                // 기존 코드에서 역할(Role) 검증 또는 관련 로직이 있었다면 이 부분에서 제거되었습니다.
                // 예: if (!"ROLE_ADMIN".equals(claims.get("role"))) { return handleUnauthorized(...); }

                for (Map.Entry<String, Object> entry : claims.entrySet()) {
                    String claimKey = "X-Claim-" + entry.getKey();
                    String claimValue = String.valueOf(entry.getValue());
                    mutatedRequest.header(claimKey, claimValue);
                    log.debug("{}: {}", claimKey, claimValue);
                    if ("address".equals(entry.getKey())) {
                        mutatedRequest.header("X-Claim-Address", claimValue);
                        log.debug("X-Claim-Address: {}", claimValue);
                    }
                }

                request = mutatedRequest.build();
                exchange = exchange.mutate().request(request).build();

            } catch (Exception e) {
                log.error("JWT validation failed", e);
                return handleUnauthorized(response, "JWT validation failed: " + e.getMessage());
            }

            log.debug("Custom PRE filter: request uri -> {}", request.getURI());
            log.debug("Custom PRE filter: request id -> {}", request.getId());

            return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                log.debug("Custom POST filter: response status code -> {}", response.getStatusCode());
            }));
        };
    }

    private Mono<Void> handleUnauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        String body = String.format("{\"error\": \"%s\", \"message\": \"%s\"}",
                HttpStatus.UNAUTHORIZED.getReasonPhrase(), message);
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes());
        return response.writeWith(Mono.just(buffer));
    }

    @Data
    public static class Config {
        private boolean preLogger;
        private boolean postLogger;
    }
}