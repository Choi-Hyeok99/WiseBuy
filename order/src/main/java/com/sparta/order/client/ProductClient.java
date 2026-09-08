package com.sparta.order.client;

import com.sparta.common.dto.ProductResponseDto;
import com.sparta.order.dto.StockUpdateRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

// fallbackFactory: 호출 실패 시 ProductClientFallbackFactory가 대신 응답한다.
// (feign.circuitbreaker.enabled=true 라야 fallback이 동작한다 — application.yml 참고)
@FeignClient(name = "product-service", fallbackFactory = ProductClientFallbackFactory.class)
public interface ProductClient {

    @GetMapping("/products/{productId}")
    ProductResponseDto getProductById(@PathVariable Long productId);

    @PutMapping("/products/{productId}/stock")
    void updateStock(@PathVariable Long productId, @RequestBody StockUpdateRequestDto stockUpdateRequestDto);
}
