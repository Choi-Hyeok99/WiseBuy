package com.sparta.common.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderResponseForPaymentDto {
    private Long id;
    private Long userId;
    private int totalAmount; // 총 금액

    public OrderResponseForPaymentDto(Long id, Long userId, int totalAmount) {
        this.id = id;
        this.userId = userId;
        this.totalAmount = totalAmount;
    }
}