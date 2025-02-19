package com.sparta.payment.service;

import com.sparta.common.dto.KafkaMessage;
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
    private final PaymentProducerService paymentProducerService;

    @Transactional
    public PaymentResponseDto processPayment(PaymentRequestDto paymentRequestDto, Long userId) {
        // 사용자 ID 검증
        if (!paymentRequestDto.getUserId().equals(userId)) {
            throw new IllegalArgumentException("사용자 ID가 일치하지 않습니다.");
        }

        // 결제 처리 로직 (80% 성공, 20% 실패)
        // boolean isPaymentSuccessful = Math.random() > 0.2; ( 실제 테스트 )
        boolean isPaymentSuccessful = Math.random() > 0.2; // 성공 테스트 확인 여부
        PaymentStatus status = isPaymentSuccessful ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;

        // Payment 엔티티 생성 및 저장
        Payment payment = new Payment();
        payment.setOrderId(paymentRequestDto.getOrderId());
        payment.setUserId(userId);
        payment.setTotalPrice(paymentRequestDto.getTotalAmount());
        payment.setStatus(status);
        payment.setPaymentDate(LocalDateTime.now());
        paymentRepository.save(payment);

        // Kafka 이벤트 발행 ( payment.success or payment.failed )
        String eventType = isPaymentSuccessful ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED";
        KafkaMessage<PaymentResponseDto> message = new KafkaMessage<>(eventType,
                new PaymentResponseDto(payment.getId(), payment.getOrderId(),payment.getStatus().name(), payment.getTotalPrice()));

        paymentProducerService.sendPaymentResult("payment.result", message);

        // 응답 DTO 생성 및 반환
        return new PaymentResponseDto(payment.getId(), payment.getOrderId(), payment.getStatus().name(), payment.getTotalPrice());    }
}