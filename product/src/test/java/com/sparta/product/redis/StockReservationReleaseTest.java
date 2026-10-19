package com.sparta.product.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 주문 생성 롤백 보상(예약 해제)의 불변식: "실제로 예약된 만큼만, 한 번만 되돌린다."
 * order는 예약 결과를 모르는 항목(타임아웃)까지 해제를 보내므로, 해제가 수량을 그대로 더하면 재고가 늘어나고
 * 해제가 예약보다 먼저 도착하면 재고가 샌다. 둘 다 주문별 예약 기록 + RELEASED 툼스톤으로 막는지 확인한다.
 *
 * 실행 전제: localhost:6379 Redis (없으면 스킵). 테스트 전용 키만 쓰고 뒤에 지운다.
 */
class StockReservationReleaseTest {

    private static final String PRODUCT = "reservation-test";
    private static final String STOCK_KEY = "product_stock:" + PRODUCT;

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
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
        } catch (Exception e) {
            assumeTrue(false, "localhost:6379 Redis 없음 — 테스트 스킵 (docker compose up -d redis 후 재실행)");
        }
        cleanUp();
        redisTemplate.opsForValue().set(STOCK_KEY, "10");
    }

    @AfterEach
    void tearDown() {
        try {
            cleanUp();
        } catch (Exception ignored) {
        }
        if (cf != null) cf.destroy();
    }

    private void cleanUp() {
        redisTemplate.delete(STOCK_KEY);
        var keys = redisTemplate.keys("resv:*:" + PRODUCT);
        if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
    }

    private int stock() {
        return Integer.parseInt(redisTemplate.opsForValue().get(STOCK_KEY));
    }

    @Test
    @DisplayName("예약 → 해제: 예약한 수량만큼 정확히 복구, 해제가 재전달돼도 한 번만")
    void releaseRestoresReservedQuantityOnce() {
        assertThat(redisUtility.reserveStockInRedis(PRODUCT, 3, 1L)).isEqualTo(7L);

        assertThat(redisUtility.releaseReservationInRedis(PRODUCT, 1L)).isEqualTo(3L);
        assertThat(redisUtility.releaseReservationInRedis(PRODUCT, 1L)).isZero();

        assertThat(stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("예약 실패(재고 부족)한 항목의 해제 → 아무것도 안 더함")
    void releaseWithoutReservationIsNoop() {
        assertThat(redisUtility.reserveStockInRedis(PRODUCT, 50, 2L)).isEqualTo(-2L);

        assertThat(redisUtility.releaseReservationInRedis(PRODUCT, 2L)).isZero();

        assertThat(stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("해제가 예약보다 먼저 도착(타임아웃 후 늦게 처리된 예약) → 늦은 예약은 거절, 재고 유지")
    void lateReservationAfterReleaseIsRejected() {
        assertThat(redisUtility.releaseReservationInRedis(PRODUCT, 3L)).isZero();

        assertThat(redisUtility.reserveStockInRedis(PRODUCT, 3, 3L)).isEqualTo(-3L);

        assertThat(stock()).isEqualTo(10);
    }

    @Test
    @DisplayName("같은 주문의 예약 재요청 → 두 번 차감하지 않음")
    void duplicateReservationIsIdempotent() {
        redisUtility.reserveStockInRedis(PRODUCT, 3, 4L);
        assertThat(redisUtility.reserveStockInRedis(PRODUCT, 3, 4L)).isEqualTo(7L);

        assertThat(stock()).isEqualTo(7);
    }

    @Test
    @DisplayName("예약·해제가 동시에 경합해도 결과는 '예약 성공 후 해제' 또는 '해제 후 예약 거절' 둘 중 하나 — 재고는 항상 원래대로")
    void concurrentReserveAndReleaseNeverLeaks() throws InterruptedException {
        int orders = 200;
        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch fire = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(orders * 2);
        redisTemplate.opsForValue().set(STOCK_KEY, String.valueOf(orders));

        for (long orderId = 1000; orderId < 1000 + orders; orderId++) {
            long id = orderId;
            pool.submit(() -> { await(fire); redisUtility.reserveStockInRedis(PRODUCT, 1, id); done.countDown(); });
            pool.submit(() -> { await(fire); redisUtility.releaseReservationInRedis(PRODUCT, id); done.countDown(); });
        }
        fire.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(stock()).isEqualTo(orders); // 모든 주문이 롤백됐으므로 재고는 처음 그대로
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
