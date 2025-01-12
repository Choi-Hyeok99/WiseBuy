package com.sparta.product.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StockUpdateRequestDto {
    private int quantity; // 음수면 감소, 양수면 증가
}