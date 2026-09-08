package com.sparta.order.client;

import com.sparta.order.dto.StockUpdateRequestDto;
import com.sparta.order.exception.ProductServiceUnavailableException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * fallback의 핵심 규칙: 하위 서비스가 준 4xx는 그대로 다시 던지고(정상 거절),
 * 연결 실패·타임아웃·서킷 오픈은 503(ProductServiceUnavailableException)으로 바꾼다.
 */
class ProductClientFallbackFactoryTest {

    private final ProductClientFallbackFactory factory = new ProductClientFallbackFactory();

    private static FeignException feignStatus(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/products/1",
                Collections.emptyMap(), null, StandardCharsets.UTF_8, new RequestTemplate());
        return FeignException.errorStatus("ProductClient#getProductById(Long)",
                feign.Response.builder()
                        .status(status)
                        .reason("x")
                        .request(request)
                        .headers(Map.of())
                        .build());
    }

    @Nested
    @DisplayName("4xx는 그대로 전파 (서킷/503으로 삼키지 않음)")
    class FourxxPassThrough {

        @Test
        @DisplayName("재고 부족 409 → FeignException 그대로")
        void conflictIsRethrown() {
            ProductClient fallback = factory.create(feignStatus(409));

            assertThatThrownBy(() -> fallback.updateStock(1L, new StockUpdateRequestDto(1)))
                    .isInstanceOf(FeignException.class)
                    .satisfies(e -> assertThat(((FeignException) e).status()).isEqualTo(409));
        }

        @Test
        @DisplayName("없는 상품 404 → FeignException 그대로")
        void notFoundIsRethrown() {
            ProductClient fallback = factory.create(feignStatus(404));

            assertThatThrownBy(() -> fallback.getProductById(1L))
                    .isInstanceOf(FeignException.class)
                    .satisfies(e -> assertThat(((FeignException) e).status()).isEqualTo(404));
        }
    }

    @Nested
    @DisplayName("장애성 원인은 503으로 변환")
    class FailuresBecome503 {

        @Test
        @DisplayName("타임아웃 → ProductServiceUnavailableException")
        void timeoutBecomesUnavailable() {
            ProductClient fallback = factory.create(new TimeoutException("read timed out"));

            assertThatThrownBy(() -> fallback.getProductById(1L))
                    .isInstanceOf(ProductServiceUnavailableException.class);
        }

        @Test
        @DisplayName("연결 거부(product 다운) → ProductServiceUnavailableException")
        void connectionRefusedBecomesUnavailable() {
            ProductClient fallback = factory.create(new RuntimeException("Connection refused"));

            assertThatThrownBy(() -> fallback.updateStock(1L, new StockUpdateRequestDto(1)))
                    .isInstanceOf(ProductServiceUnavailableException.class);
        }

        @Test
        @DisplayName("product 5xx → ProductServiceUnavailableException")
        void serverErrorBecomesUnavailable() {
            ProductClient fallback = factory.create(feignStatus(500));

            assertThatThrownBy(() -> fallback.getProductById(1L))
                    .isInstanceOf(ProductServiceUnavailableException.class);
        }
    }
}
