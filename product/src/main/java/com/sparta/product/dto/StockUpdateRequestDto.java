package com.sparta.product.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // 🔥 알 수 없는 필드는 무시!
public class StockUpdateRequestDto {
    private Long orderId; // 추가된 필드
    private Long productId;
    private int quantity; // 음수면 감소, 양수면 증가
}