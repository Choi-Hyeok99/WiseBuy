package com.sparta.order.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockUpdateRequestDto {
    private int quantity; // 재고 변화량 (양수: 증가, 음수: 감소)
}