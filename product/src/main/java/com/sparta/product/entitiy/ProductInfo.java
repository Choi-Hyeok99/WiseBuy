package com.sparta.product.entitiy;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class ProductInfo {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productInfoId; // 기본 키

    @Column(nullable = false)
    private Integer price; // 상품 가격

    @Column(nullable = true, columnDefinition = "TEXT")
    private String description; // 상품설명

    @Column(nullable = true)
    private String imagePath; // 상품 사진

    @OneToOne
    @JoinColumn(name = "product_id")
    private Product product;  // Product 와 1:1 관계


}
