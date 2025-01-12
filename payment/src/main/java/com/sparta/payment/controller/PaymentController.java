package com.sparta.payment.controller;

import com.sparta.payment.dto.PaymentRequestDto;
import com.sparta.payment.dto.PaymentResponseDto;
import com.sparta.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;


    @PostMapping
    public ResponseEntity<PaymentResponseDto> createPayment(
            @RequestBody PaymentRequestDto paymentRequestDto,
            @RequestHeader(value = "X-Claim-sub", required = false) String userId) {

        if (userId == null) {
            throw new IllegalArgumentException("헤더 'X-Claim-sub'가 없습니다.");
        }

        Long parsedUserId = Long.parseLong(userId);
        PaymentResponseDto paymentResponse = paymentService.processPayment(paymentRequestDto, parsedUserId);
        return ResponseEntity.ok(paymentResponse);
    }
}
