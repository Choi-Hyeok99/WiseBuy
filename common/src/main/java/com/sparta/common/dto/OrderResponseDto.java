package com.sparta.common.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderResponseDto {
    private Long id;
    private Long userId;
    private int totalAmount; // 총 금액


}