package com.sparta.order.exception;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OrderGlobalExceptionHandler {

    // product 서비스 연결 실패·타임아웃 → fallback이 던지는 예외. 재고 부족(409)과 달리
    // "우리 잘못이 아니라 하위 서비스 장애"라서 503으로 내보낸다.
    @ExceptionHandler(ProductServiceUnavailableException.class)
    public ResponseEntity<String> handleProductUnavailable(ProductServiceUnavailableException ex){
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ex.getMessage());
    }

    // 서킷이 열려 호출 자체가 차단된 경우 (fallback을 안 타는 경로 대비).
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<String> handleCircuitOpen(CallNotPermittedException ex){
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("일시적으로 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    // fallback을 안 붙인 Feign 클라이언트(wishlist/payment)의 호출이 서킷 브레이커에서 실패한 경우.
    // 감싼 원인이 하위 서비스의 4xx면 그 상태를 살리고, 그 외(서킷 오픈·연결 실패)는 503.
    @ExceptionHandler(NoFallbackAvailableException.class)
    public ResponseEntity<String> handleNoFallback(NoFallbackAvailableException ex){
        Throwable cause = ex.getCause();
        if (cause instanceof FeignException fe) {
            return handleFeign(fe);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("일시적으로 요청을 처리할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<String> handleOrderNotFoundException(OrderNotFoundException ex){
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<String> handlerInvalidOrderStateException(InvalidOrderStateException ex){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    // 재고 부족 / 구매 불가 상태 등 "지금은 주문할 수 없음" (fetchWishlist·processOrderItems의 사전 검증,
    // product가 돌려준 재고 부족). 500이 아니라 409로 내보내 장애와 구분되게 한다.
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex){
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
    }

    // 다른 서비스(product 등) 호출 실패를 그 서비스가 준 상태 코드로 되돌려준다.
    // 특히 재고 부족(product가 409)이 여기 없으면 주문 API가 500으로 나가 "재고 소진"과 "장애"가 구분되지 않는다.
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<String> handleFeign(FeignException ex){
        HttpStatus status = HttpStatus.resolve(ex.status());
        if (status == null || status.value() < 400) {
            status = HttpStatus.BAD_GATEWAY;
        }
        String body = ex.status() == 409 ? "재고가 부족하거나 지금은 주문할 수 없습니다." : "주문 처리 중 오류가 발생했습니다.";
        return ResponseEntity.status(status).body(body);
    }
}
