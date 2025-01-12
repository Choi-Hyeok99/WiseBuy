package com.sparta.payment.service;

import com.sparta.common.dto.OrderResponseForPaymentDto;
import com.sparta.payment.client.OrderClient;
import com.sparta.payment.dto.PaymentRequestDto;
import com.sparta.payment.dto.PaymentResponseDto;
import com.sparta.payment.entity.Payment;
import com.sparta.payment.entity.PaymentStatus;
import com.sparta.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderClient orderClient; // OrderClient 의존성 추가

    @Transactional
    public PaymentResponseDto processPayment(PaymentRequestDto paymentRequestDto, Long userId) {
        // 사용자 ID 검증
        if (!paymentRequestDto.getUserId().equals(userId)) {
            throw new IllegalArgumentException("사용자 ID가 일치하지 않습니다.");
        }

        // 결제 처리 로직 (80% 성공, 20% 실패)
//        boolean isPaymentSuccessful = Math.random() > 0.2; ( 실제 테스트 )
        boolean isPaymentSuccessful = Math.random() > 0.01; // 성공 테스트 확인 여부
        PaymentStatus status = isPaymentSuccessful ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        // Payment 엔티티 생성 및 저장
        Payment payment = new Payment();
        payment.setOrderId(paymentRequestDto.getOrderId());
        payment.setUserId(userId);
        payment.setTotalPrice(paymentRequestDto.getTotalAmount());
        payment.setStatus(status);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        // 주문 상태 업데이트 (Feign Client 호출)
        try {
            orderClient.updateOrderStatus(paymentRequestDto.getOrderId(), isPaymentSuccessful, userId);
        } catch (Exception e) {
            // 예외 처리: Order 상태 업데이트 실패
            throw new IllegalStateException("주문 상태 업데이트 중 문제가 발생했습니다.", e);
        }

        // 응답 DTO 생성 및 반환
        PaymentResponseDto responseDto = new PaymentResponseDto();
        responseDto.setPaymentId(payment.getId());
        responseDto.setOrderId(paymentRequestDto.getOrderId());
        responseDto.setStatus(status.name());
        responseDto.setTotalAmount(paymentRequestDto.getTotalAmount());

        return responseDto;
    }
}