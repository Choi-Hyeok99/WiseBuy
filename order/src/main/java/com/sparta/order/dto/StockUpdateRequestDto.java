package com.sparta.order.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockUpdateRequestDto {
    private Long orderId; // 주문별 예약 기록 키. 주문 생성 롤백 시 실제로 예약된 만큼만 해제하는 근거
    private int quantity; // 차감할 수량 (양수)
}