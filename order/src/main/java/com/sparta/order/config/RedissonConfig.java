package com.sparta.order.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

    // application.yml의 spring.data.redis.host/port(도커에서는 REDIS_HOST=redis)를 그대로 쓴다.
    // 예전엔 "redis://localhost:6379"가 코드에 직접 박혀 있어서 도커에서 절대 연결이 안 됐다.
    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redisHost + ":" + redisPort);
        return Redisson.create(config);
    }
}
