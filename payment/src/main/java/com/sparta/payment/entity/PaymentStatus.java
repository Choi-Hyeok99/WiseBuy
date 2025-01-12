package com.sparta.payment.entity;

public enum PaymentStatus {
    SUCCESS,
    FAILED,      // 결제 실패
    IN_PROGRESS,// 결제 진행 중 (필요 시)
    CANCELLED   // 결제 취소 (필요 시)
}
