package com.sparta.product.entitiy;

import com.sparta.product.dto.ProductDetailResponseDto;
import com.sparta.product.dto.ProductRequestDto;
import com.sparta.product.dto.ProductResponseDto;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class Product {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id" , nullable = false, unique = true)
    private Long id; // 상품번호

    @Column(nullable = false, length = 100)
    private String productName; // 상품이름

    @Column
    private int stock; // 상품 수량

    @Column(nullable = true)
    private LocalDateTime startTime; // 판매 시작 시간

    @Column(nullable = true)
    private LocalDateTime endTime; // 판매 종료 시간

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ProductInfo productInfo; // 상세 정보와 1:1 관계

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status; // 선착순 오픈 or 클로즈


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType productType; // 상품 유형


    // 상태 업데이트 메서드
    public void updateStatus() {
        LocalDateTime now = LocalDateTime.now();
        if (startTime != null && endTime != null) {
            if (now.isAfter(startTime) && now.isBefore(endTime)) {
                this.status = ProductStatus.AVAILABLE;
            } else {
                this.status = ProductStatus.UNAVAILABLE;
            }
        } else {
            this.status = ProductStatus.UNAVAILABLE;
        }
    }

    // DTO -> Entity 변환
    public static Product fromRequestDto(ProductRequestDto requestDto){
        Product product = new Product();
        product.productName = requestDto.getProductName();
        product.stock = requestDto.getStock();
        product.startTime = requestDto.getStartTime();
        product.endTime = requestDto.getEndTime();

        ProductInfo productInfo = new ProductInfo();
        productInfo.setPrice(requestDto.getPrice());
        productInfo.setDescription(requestDto.getDescription());
        productInfo.setImagePath(requestDto.getImagePath());
        productInfo.setProduct(product); // 관계 설정

        product.setProductInfo(productInfo); // Product와 관계 설정

        // 추가된 부분: productType과 status 설정
        product.productType = ProductType.valueOf(requestDto.getProductType().toUpperCase());
        product.status = ProductStatus.valueOf(requestDto.getStatus().toUpperCase());


        return product;
    }
    // Entity -> DTO 변환
    public ProductResponseDto toResponseDto() {
        return new ProductResponseDto(
                this.id,
                this.productName,
                this.stock,
                this.productInfo != null ? this.productInfo.getPrice() : null,
                this.productInfo != null ? this.productInfo.getImagePath() : null,
                this.startTime != null ? this.startTime.toLocalDate()
                                                       .atStartOfDay() : null,
                this.endTime != null ? this.endTime.toLocalDate()
                                                   .atStartOfDay() : null,
            this.status.name(), // 상태 추가
                this.productType.name() // 상품 유형 추가
        );
    }
    public ProductDetailResponseDto toDetailResponseDto() {
        return new ProductDetailResponseDto(
                this.id,
                this.productName,
                this.stock,
                this.productInfo.getPrice(),
                this.productInfo.getDescription(),
                this.productInfo.getImagePath(),
                this.startTime != null ? this.startTime.toString() : null,
                this.endTime != null ? this.endTime.toString() : null,
                this.status.name(), // 상태 추가
                this.productType.name() // 상품 유형 추가
        );
    }
    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
