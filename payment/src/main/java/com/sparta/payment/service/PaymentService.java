package com.sparta.payment.service;

import com.sparta.common.dto.KafkaMessage;
import com.sparta.payment.dto.PaymentRequestDto;
import com.sparta.payment.dto.PaymentResponseDto;
import com.sparta.payment.entity.Payment;
import com.sparta.payment.entity.PaymentStatus;
import com.sparta.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProducerService paymentProducerService;

    /**
     * 결제 실패로 처리할 비율(0.0~1.0). 실제 PG 연동이 없는 데모라 값으로 흉내낸다.
     * 기본 0 = 항상 성공(부하테스트용). SAGA 보상 트랜잭션을 확인하려면 0.2 등으로 올린다.
     */
    @Value("${payment.failure-rate:0}")
    private double failureRate;

    @Transactional
    public PaymentResponseDto processPayment(PaymentRequestDto paymentRequestDto, Long userId) {
        if (!paymentRequestDto.getUserId().equals(userId)) {
            throw new IllegalArgumentException("사용자 ID가 일치하지 않습니다.");
        }

        boolean isPaymentSuccessful = ThreadLocalRandom.current().nextDouble() >= failureRate;
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

        return new PaymentResponseDto(payment.getId(), payment.getOrderId(), payment.getStatus().name(), payment.getTotalPrice());
    }
}