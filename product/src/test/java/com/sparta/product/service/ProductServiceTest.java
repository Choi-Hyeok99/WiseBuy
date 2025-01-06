package com.sparta.product.service;

import com.sparta.product.dto.ProductRequestDto;
import com.sparta.product.dto.ProductResponseDto;
import com.sparta.product.entitiy.Product;
import com.sparta.product.entitiy.ProductType;
import com.sparta.product.redis.RedisUtility;
import com.sparta.product.repository.ProductRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.*;

    class ProductServiceTest {

        @InjectMocks
        private ProductService productService;

        @Mock
        private ProductRepository productRepository;

        @Mock
        private RedisUtility redisUtility;

        @Mock
        private HttpServletRequest request;

        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this); // Mock 초기화
        }

        @Test
        void createProduct_shouldSaveProductAndReturnResponse() {
            // Given
            ProductRequestDto requestDto = new ProductRequestDto(
                    "Test Product",        // 제품 이름
                    10,                    // 재고 수량
                    1000,                  // 가격
                    "Test Description",    // 상세 설명
                    null,                  // 판매 시작 시간 (null 처리)
                    null,                  // 판매 종료 시간 (null 처리)
                    "/images/test.jpg",    // 이미지 경로
                    "AVAILABLE",           // 초기 상태
                    ProductType.GENERAL.name() // 일반 상품 유형
            );

            // Mock 설정
            when(request.getHeader("X-Claim-sub")).thenReturn("1"); // 사용자 ID 반환
            Product mockProduct = Product.fromRequestDto(requestDto); // DTO -> Entity 변환
            mockProduct.setId(1L); // 저장 후 ID 설정
            when(productRepository.save(any(Product.class))).thenReturn(mockProduct); // 저장 로직 Mock


            // When
            ProductResponseDto result = productService.createProduct(requestDto, request);

            // Then
            // ArgumentCaptor로 RedisUtility의 호출 인자를 캡처
            ArgumentCaptor<String> stockKeyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Integer> stockValueCaptor = ArgumentCaptor.forClass(Integer.class);

            // verify로 RedisUtility 메서드 호출 검증
            verify(redisUtility).saveToCache(stockKeyCaptor.capture(), stockValueCaptor.capture());

            // 캡처된 값 검증
            assertEquals("product_stock:1", stockKeyCaptor.getValue());
            assertEquals(10, stockValueCaptor.getValue());
        }
    }