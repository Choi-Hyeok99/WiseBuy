package com.sparta.order.exception;

/**
 * product 서비스가 응답하지 않거나(연결 실패·타임아웃) 서킷이 열려 호출 자체가 차단된 경우.
 * "재고 부족(409)" 같은 정상 거절과 구분하려고 별도 예외로 둔다 → 핸들러에서 503으로 매핑.
 */
public class ProductServiceUnavailableException extends RuntimeException {
    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
