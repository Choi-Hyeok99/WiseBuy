package com.sparta.product.controller;


import com.sparta.product.dto.ProductDetailResponseDto;
import com.sparta.product.dto.ProductRequestDto;
import com.sparta.product.dto.ProductResponseDto;
import com.sparta.product.dto.StockUpdateRequestDto;
import com.sparta.product.service.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {


    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(@RequestBody ProductRequestDto requestDto,HttpServletRequest request) {
        ProductResponseDto productResponseDto = productService.createProduct(requestDto,request);
        return new ResponseEntity<>(productResponseDto, HttpStatus.CREATED);
    }

    // 제품 조회
    @GetMapping
    public ResponseEntity<Map<String, Object>> getProductList(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size
    ) {
        Page<ProductResponseDto> products = productService.getProductList(page, size);

        // 메타데이터와 데이터 분리
        Map<String, Object> response = new HashMap<>();
        response.put("data", products.getContent());
        response.put("totalPages", products.getTotalPages());
        response.put("totalElements", products.getTotalElements());
        response.put("currentPage", products.getNumber());

        return ResponseEntity.ok(response);
    }

    // 상세 조회
    @GetMapping("/{productId}")
    public ResponseEntity<ProductDetailResponseDto> getProductDetails(@PathVariable Long productId){
        ProductDetailResponseDto productDetail = productService.getProductDetails(productId);
        return ResponseEntity.ok(productDetail);
    }
    @PutMapping("/{productId}/stock")
    public ResponseEntity<String> updateStock(@PathVariable Long productId, @RequestBody StockUpdateRequestDto stockUpdateRequestDto) {
        try {
            // 재고 업데이트 서비스 호출
            productService.updateStockWithDistributedLock(productId, stockUpdateRequestDto.getQuantity());
            return ResponseEntity.ok().build();
        } catch (IllegalStateException e) {
            // 락을 얻지 못한 경우 (다른 요청이 작업 중일 때)
            return ResponseEntity.status(HttpStatus.CONFLICT).body("다른 요청이 재고를 수정 중입니다. 잠시 후 다시 시도해주세요.");
        } catch (IllegalArgumentException e) {
            // 재고 부족 등의 잘못된 요청 처리
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            // 그 외 예외 처리
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    @GetMapping("/{productId}/stock")
    public ResponseEntity<Integer> getProductStock(@PathVariable Long productId){
        int stock = productService.getProductStock(productId);
        return ResponseEntity.ok(stock);
    }
}
