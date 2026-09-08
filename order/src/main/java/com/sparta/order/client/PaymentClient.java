package com.sparta.order.client;

import com.sparta.order.dto.PaymentRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

// url을 직접 박아두면 Eureka를 거치지 않고 항상 이 주소로만 호출하는데, 컨테이너 안에서
// localhost는 order 컨테이너 자기 자신이라 payment로 절대 연결이 안 된다.
// WishlistClient/ProductClient처럼 name만 남겨서 Eureka + 로드밸런서로 실제 주소를 찾게 한다.
@FeignClient(name = "payment-service")
public interface PaymentClient {

    @PostMapping("/payments")
    void sendPaymentRequest(
            @RequestBody PaymentRequestDto paymentRequestDto,
            @RequestHeader("X-Claim-sub") String userId
    );
}