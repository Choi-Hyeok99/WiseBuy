package com.sparta.order.client;

import com.sparta.order.dto.PaymentRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "payment-service", url = "http://localhost:8085")
public interface PaymentClient {

    @PostMapping("/payments")
    void sendPaymentRequest(
            @RequestBody PaymentRequestDto paymentRequestDto,
            @RequestHeader("X-Claim-sub") String userId
    );
}