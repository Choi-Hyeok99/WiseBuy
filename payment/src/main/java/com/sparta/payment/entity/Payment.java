package com.sparta.payment.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 결제 ID

    @Column(nullable = false)
    private Long orderId; // 주문 ID

    @Column(nullable = false)
    private Long userId; // 사용자 ID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status; // 결제 상태 (예: SUCCESS, FAILED)


    @Column(nullable = true)
    private int totalPrice; // 결제 금액


    @Column(nullable = true)
    private LocalDateTime paymentDate; // 결제 완료 / 실패 시각

}
