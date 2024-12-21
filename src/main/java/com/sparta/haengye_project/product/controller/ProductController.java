package com.sparta.haengye_project.product.controller;

import com.sparta.haengye_project.product.dto.ProductRequestDto;
import com.sparta.haengye_project.product.dto.ProductResponseDto;
import com.sparta.haengye_project.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(@RequestBody ProductRequestDto requestDto){
            ProductResponseDto productResponseDto = productService.createProduct(requestDto);
            return new ResponseEntity<>(productResponseDto, HttpStatus.CREATED); // 201 반환 ( 제품 생성 )
    }

    // 제품 조회
    @GetMapping
    public ResponseEntity<Page<ProductResponseDto>> getProductList(
            @RequestParam(value = "page", defaultValue = "0")int page,
            @RequestParam(value = "size", defaultValue = "10")int size
    ){
        Page<ProductResponseDto> products = productService.getProductList(page,size);
        return ResponseEntity.ok(products);
    }
}
