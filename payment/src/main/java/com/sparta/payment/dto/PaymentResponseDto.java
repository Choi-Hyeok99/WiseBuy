package com.sparta.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
public class PaymentResponseDto {
    private Long paymentId;   // 결제 ID
    private Long orderId;     // 주문 ID
    private String status;    // 결제 상태 (SUCCESS, FAILED)
    private int totalAmount;

    public PaymentResponseDto(Long paymentId, Long orderId, String status, int totalAmount) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.status = status;
        this.totalAmount = totalAmount;
    }
}
