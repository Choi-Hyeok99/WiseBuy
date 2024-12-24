package com.sparta.haengye_project.order.entity;

public enum OrderStatus {
    PENDING,     // 주문 대기 중
    SHIPPED,
    RETURN_REQUESTED,
    CANCELLED,   // 주문 취소
    COMPLETED    // 주문 완료
}
