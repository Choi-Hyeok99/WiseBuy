package com.sparta.haengye_project.product.entitiy;

import com.sparta.haengye_project.product.dto.ProductDetailResponseDto;
import com.sparta.haengye_project.product.dto.ProductRequestDto;
import com.sparta.haengye_project.product.dto.ProductResponseDto;
import com.sparta.haengye_project.user.entity.User;
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

    // **N:1 관계 매핑**
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;


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
                                                   .atStartOfDay() : null
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
                this.endTime != null ? this.endTime.toString() : null
        );
    }
}
