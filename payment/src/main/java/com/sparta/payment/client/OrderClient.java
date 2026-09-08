package com.sparta.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

// url을 직접 박아두면 Eureka를 거치지 않고 항상 이 주소로만 호출하는데, 컨테이너 안에서
// localhost는 payment 컨테이너 자기 자신이라 order로 절대 연결이 안 된다.
// name만 남겨서 Eureka + 로드밸런서로 실제 주소를 찾게 한다.
@FeignClient(name = "order-service")
public interface OrderClient {

    @PostMapping("/orders/{orderId}/update-status")
    void updateOrderStatus(@PathVariable("orderId") Long orderId,
                           @RequestParam("isPaymentSuccessful") boolean isPaymentSuccessful,
                           @RequestHeader("X-Claim-sub") Long userId);
}