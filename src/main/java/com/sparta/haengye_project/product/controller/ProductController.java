package com.sparta.haengye_project.product.controller;

import com.sparta.haengye_project.product.dto.ProductDetailResponseDto;
import com.sparta.haengye_project.product.dto.ProductRequestDto;
import com.sparta.haengye_project.product.dto.ProductResponseDto;
import com.sparta.haengye_project.product.service.ProductService;
import com.sparta.haengye_project.user.entity.User;
import com.sparta.haengye_project.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final UserService userService; // UserService 선언

    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(@RequestBody ProductRequestDto requestDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName(); // JWT에서 추출한 이메일
        User user = userService.findUserByEmail(email); // 이메일로 사용자 조회
        ProductResponseDto productResponseDto = productService.createProduct(requestDto, user);
        return new ResponseEntity<>(productResponseDto, HttpStatus.CREATED);
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
    @GetMapping("/{productId}")
    public ResponseEntity<ProductDetailResponseDto> getProductDetails(@PathVariable Long productId){
        ProductDetailResponseDto productDetail = productService.getProductDetails(productId);
        return ResponseEntity.ok(productDetail);
    }
}
