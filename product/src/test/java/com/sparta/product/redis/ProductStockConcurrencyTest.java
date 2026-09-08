package com.sparta.product.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 선착순 재고 차감의 핵심 불변식 검증:
 *   "여러 요청이 동시에 몰려도 재고가 음수가 되지 않고(오버셀 없음), 딱 재고 수량만큼만 성공한다."
 *
 * Mock이 아니라 실제 Redis 위에서 RedisUtility의 Lua 스크립트를 그대로 실행한다.
 * Redis가 단일 스레드로 Lua를 원자 실행하므로, 조회→비교→차감 사이에 다른 요청이 끼어들 수 없다는 게 요점.
 *
 * 실행 전제: localhost:6379 에 Redis가 떠 있어야 한다 (`docker compose up -d redis`).
 * 없으면 테스트를 스킵한다(Assumptions). 테스트 전용 키(product_stock:concurrency-test-*)만 쓰고 뒤에 지운다.
 */
class ProductStockConcurrencyTest {

    private static final String KEY_A = "concurrency-test-a";
    private static final String KEY_B = "concurrency-test-b";

    private RedisUtility redisUtility;
    private StringRedisTemplate redisTemplate;
    private LettuceConnectionFactory cf;

    @BeforeEach
    void setUp() {
        cf = new LettuceConnectionFactory("localhost", 6379);
        cf.afterPropertiesSet();
        cf.start();
        redisTemplate = new StringRedisTemplate(cf);
        redisTemplate.afterPropertiesSet();
        redisUtility = new RedisUtility(redisTemplate, new ObjectMapper());

        // Redis 안 떠 있으면 스킵
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
        } catch (Exception e) {
            assumeTrue(false, "localhost:6379 Redis 없음 — 테스트 스킵 (docker compose up -d redis 후 재실행)");
        }
        redisTemplate.delete(java.util.List.of("product_stock:" + KEY_A, "product_stock:" + KEY_B));
    }

    @AfterEach
    void tearDown() {
        try {
            redisTemplate.delete(java.util.List.of("product_stock:" + KEY_A, "product_stock:" + KEY_B));
        } catch (Exception ignored) {
        }
        if (cf != null) cf.destroy();
    }

    @Test
    @DisplayName("재고 100, 동시 요청 800건 → 정확히 100건만 성공 / 700건 거절 / 최종 재고 0 (음수 아님)")
    void noOversellUnderConcurrency() throws InterruptedException {
        int initialStock = 100;
        int concurrentRequests = 800;
        redisUtility.saveToCache("product_stock:" + KEY_A, initialStock);

        ExecutorService pool = Executors.newFixedThreadPool(40);
        CountDownLatch ready = new CountDownLatch(concurrentRequests);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < concurrentRequests; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    fire.await();
                    Long remaining = redisUtility.updateStockInRedis(KEY_A, 1);
                    if (remaining != null && remaining >= 0) success.incrementAndGet();
                    else if (remaining != null && remaining == -2) rejected.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        fire.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        int finalStock = Integer.parseInt(redisTemplate.opsForValue().get("product_stock:" + KEY_A));

        assertThat(success.get()).isEqualTo(initialStock);
        assertThat(rejected.get()).isEqualTo(concurrentRequests - initialStock);
        assertThat(finalStock).isZero();
        assertThat(finalStock).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("차감(-)과 복구(+)가 동시에 섞여 들어와도 최종 재고는 산술적으로 정확하다")
    void concurrentDecrementAndRollbackStayConsistent() throws InterruptedException {
        int initialStock = 500;
        int decrements = 300;
        int rollbacks = 100;
        redisUtility.saveToCache("product_stock:" + KEY_B, initialStock);

        ExecutorService pool = Executors.newFixedThreadPool(40);
        CountDownLatch done = new CountDownLatch(decrements + rollbacks);

        for (int i = 0; i < decrements; i++) {
            pool.submit(() -> { redisUtility.updateStockInRedis(KEY_B, 1); done.countDown(); });
        }
        for (int i = 0; i < rollbacks; i++) {
            pool.submit(() -> { redisUtility.rollbackStockInRedis(KEY_B, 1); done.countDown(); });
        }

        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        int finalStock = Integer.parseInt(redisTemplate.opsForValue().get("product_stock:" + KEY_B));
        assertThat(finalStock).isEqualTo(initialStock - decrements + rollbacks); // 500 - 300 + 100 = 300
    }
}
