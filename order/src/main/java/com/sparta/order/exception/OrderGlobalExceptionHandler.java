package com.sparta.order.exception;

import feign.FeignException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OrderGlobalExceptionHandler {

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
