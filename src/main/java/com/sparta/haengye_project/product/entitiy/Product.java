package com.sparta.haengye_project.product.entitiy;

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
    @Column(nullable = false, unique = true)
    private Long product_id; // 상품번호

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


}
