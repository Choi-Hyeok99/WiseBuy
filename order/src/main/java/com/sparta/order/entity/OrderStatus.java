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
    PAYMENT_FAILED,
    EXPIRED; // 결제 대기 시간 초과로 자동 취소

    /**
     * 결제 결과를 받아도 되는 상태인지. 이 상태에서만 결제 결과 반영·취소·만료로 넘어갈 수 있다.
     * prepare-payment 없이 바로 결제하는 흐름(k6 order-flow 등)도 있어서 PENDING도 포함한다.
     * 이미 취소·만료·실패된 주문에 결제 결과가 늦게 오면 이 가드로 걸러 재고가 두 번 복구되지 않게 한다.
     */
    public boolean isAwaitingPayment() {
        return this == PENDING || this == PAYMENT_PENDING;
    }
}
