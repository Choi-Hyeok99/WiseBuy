package com.sparta.haengye_project.product.dto;

import lombok.Data;


@Data
public class ProductDetailResponseDto {

    private Long productId;       // 상품 ID
    private String productName;   // 상품 이름
    private Integer stock;        // 재고
    private Integer price;        // 가격
    private String description;   // 상품 설명
    private String imagePath;     // 상품 이미지 경로
    private String startTime;     // 판매 시작 시간
    private String endTime;       // 판매 종료 시간

    public ProductDetailResponseDto(Long productId, String productName, Integer stock, Integer price,
                                    String description, String imagePath, String startTime, String endTime){
        this.productId = productId;
        this.productName = productName;
        this.stock = stock;
        this.price = price;
        this.description = description;
        this.imagePath = imagePath;
        this.startTime = startTime;
        this.endTime = endTime;
    }
}
