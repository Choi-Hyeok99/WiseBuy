package com.sparta.order.client;

import com.sparta.common.dto.ProductResponseDto;
import com.sparta.order.dto.StockUpdateRequestDto;
import com.sparta.order.exception.ProductServiceUnavailableException;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * ProductClient 호출이 실패했을 때 대신 실행되는 fallback.
 *
 * 여기서 "가짜 성공"을 돌려주면 안 된다 — 재고 정보 없이 주문이 진행되거나, 재고를 안 깎고
 * 주문이 완료돼 버린다. 그래서 fallback의 역할은 딱 두 가지다:
 *   1) product가 준 4xx(예: 재고 부족 409)는 그대로 다시 던진다 → 기존 FeignException 핸들러가 처리
 *   2) 연결 실패·타임아웃·서킷 오픈은 503으로 바꿔서 빠르게 실패시킨다 (스레드 안 물리게)
 */
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    private static final Logger log = LoggerFactory.getLogger(ProductClientFallbackFactory.class);

    @Override
    public ProductClient create(Throwable cause) {
        return new ProductClient() {
            @Override
            public ProductResponseDto getProductById(Long productId) {
                throw translate(cause, "상품 정보를 조회하지 못했습니다. productId=" + productId);
            }

            @Override
            public void updateStock(Long productId, StockUpdateRequestDto stockUpdateRequestDto) {
                throw translate(cause, "재고를 반영하지 못했습니다. productId=" + productId);
            }
        };
    }

    private RuntimeException translate(Throwable cause, String context) {
        // product가 정상적으로 4xx를 응답한 경우(재고 부족 409, 없는 상품 404 등)는 장애가 아니다.
        // 그대로 다시 던져서 OrderGlobalExceptionHandler가 상태 코드를 살려주도록 한다.
        if (cause instanceof FeignException fe && fe.status() >= 400 && fe.status() < 500) {
            return fe;
        }
        log.warn("product 호출 실패 → fallback ({}). 원인: {}", context, cause.toString());
        return new ProductServiceUnavailableException(
                "상품 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.", cause);
    }
}
