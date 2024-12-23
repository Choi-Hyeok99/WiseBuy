package com.sparta.haengye_project.order.entity;


public enum OrderItemStatus {
    ORDERED,     // 주문 접수
    SHIPPED,     // 배송 중
    DELIVERED,   // 배송 완료
    RETURNED     // 반품 완료
}
