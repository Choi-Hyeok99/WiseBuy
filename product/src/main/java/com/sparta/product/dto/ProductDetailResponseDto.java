package com.sparta.product.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;


@Data
public class ProductDetailResponseDto {

    private Long id;       // 상품 ID
    private String productName;   // 상품 이름
    private Integer stock;        // 재고
    private Integer price;        // 가격
    private String description;   // 상품 설명
    private String imagePath;     // 상품 이미지 경로
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private String startTime;     // 판매 시작 시간
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private String endTime;       // 판매 종료 시간
    private String status;            // 상품 상태 (추가)
    private String productType; // 추가



    public ProductDetailResponseDto(Long id, String productName, Integer stock, Integer price,
                                    String description, String imagePath, String startTime, String endTime,
                                    String status,String productType){
        this.id = id;
        this.productName = productName;
        this.stock = stock;
        this.price = price;
        this.description = description;
        this.imagePath = imagePath;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status; // 상태값 추가
        this.productType = productType;



    }

}
