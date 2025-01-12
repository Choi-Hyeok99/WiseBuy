package com.sparta.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequestDto {
    private Long orderId;    // 주문 ID
    private Long userId;
    private int totalAmount; // 총 결제 금액
}
