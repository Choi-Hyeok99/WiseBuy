package com.sparta.product.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 재고 차감의 핵심 불변식 검증:
 *   "여러 요청이 동시에 몰려도 재고가 음수가 되지 않고(오버셀 없음), 딱 재고 수량만큼만 성공한다."
 *
 * Mock이 아니라 실제 Redis(Testcontainers) 위에서 RedisUtility의 Lua 스크립트를 그대로 실행한다.
 * Redis가 단일 스레드로 Lua를 원자 실행하므로, 조회→비교→차감 사이에 다른 요청이 끼어들 수 없다는 게 요점.
 * 실행에 Docker 필요.
 */
@Testcontainers
class ProductStockConcurrencyTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private RedisUtility redisUtility;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory cf = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getFirstMappedPort());
        cf.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(cf);
        redisTemplate.afterPropertiesSet();
        redisUtility = new RedisUtility(redisTemplate, new ObjectMapper());
    }

    @Test
    @DisplayName("재고 100, 동시 요청 1000건 → 정확히 100건만 성공 / 900건 거절 / 최종 재고 0 (음수 아님)")
    void noOversellUnderConcurrency() throws InterruptedException {
        String productId = "1";
        int initialStock = 100;
        int concurrentRequests = 1000;
        redisUtility.saveToCache("product_stock:" + productId, initialStock); // Lua가 보는 키 형식

        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch fire = new CountDownLatch(1); // 모든 스레드가 준비되면 동시에 출발
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < concurrentRequests; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    fire.await();
                    Long remaining = redisUtility.updateStockInRedis(productId, 1);
                    if (remaining != null && remaining >= 0) {
                        success.incrementAndGet();
                    } else if (remaining != null && remaining == -2) {
                        rejected.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        fire.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int finalStock = Integer.parseInt(redisTemplate.opsForValue().get("product_stock:" + productId));

        assertThat(success.get()).isEqualTo(initialStock);                       // 딱 100건
        assertThat(rejected.get()).isEqualTo(concurrentRequests - initialStock); // 나머지 900건
        assertThat(finalStock).isZero();                                         // 재고 0
        assertThat(finalStock).isGreaterThanOrEqualTo(0);                        // 절대 음수 아님
    }

    @Test
    @DisplayName("차감(-)과 복구(+)가 동시에 섞여 들어와도 최종 재고는 산술적으로 정확하다")
    void concurrentDecrementAndRollbackStayConsistent() throws InterruptedException {
        String productId = "2";
        int initialStock = 500;
        int decrements = 300;
        int rollbacks = 100;
        redisUtility.saveToCache("product_stock:" + productId, initialStock);

        ExecutorService pool = Executors.newFixedThreadPool(40);
        CountDownLatch done = new CountDownLatch(decrements + rollbacks);

        for (int i = 0; i < decrements; i++) {
            pool.submit(() -> { redisUtility.updateStockInRedis(productId, 1); done.countDown(); });
        }
        for (int i = 0; i < rollbacks; i++) {
            pool.submit(() -> { redisUtility.rollbackStockInRedis(productId, 1); done.countDown(); });
        }

        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        int finalStock = Integer.parseInt(redisTemplate.opsForValue().get("product_stock:" + productId));
        assertThat(finalStock).isEqualTo(initialStock - decrements + rollbacks); // 500 - 300 + 100 = 300
    }
}
