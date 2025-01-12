package com.sparta.order.client;

import com.sparta.common.dto.ProductResponseDto;
import com.sparta.order.dto.StockUpdateRequestDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "product-service")
public interface ProductClient {

    @GetMapping("/products/{productId}")
    ProductResponseDto getProductById(@PathVariable Long productId);

    @PutMapping("/products/{productId}/stock")
    void updateStock(@PathVariable Long productId, @RequestBody StockUpdateRequestDto stockUpdateRequestDto);
}
