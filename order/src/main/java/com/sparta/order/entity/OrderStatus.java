package com.sparta.order.entity;

public enum OrderStatus {
    PENDING,     // 주문 대기 중
    SHIPPED,
    RETURN_REQUESTED,
    RETURN_NOT_ALLOWED,
    RETURN, // 반품 완료
    CANCELLED,   // 주문 취소
    COMPLETED,   // 주문 완료
    PAYMENT_PENDING, // 결제 준비
    PAYMENT_COMPLETED, // 결제 완료
    PAYMENT_FAILED
}
