package com.sparta.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "order-service", url = "http://localhost:8084")
public interface OrderClient {

    @PostMapping("/orders/{orderId}/update-status")
    void updateOrderStatus(@PathVariable("orderId") Long orderId,
                           @RequestParam("isPaymentSuccessful") boolean isPaymentSuccessful,
                           @RequestHeader("X-Claim-sub") Long userId);
}