package com.sparta.haengye_project.product.dto;

import lombok.Getter;
import lombok.Setter;


import java.time.LocalDateTime;

@Getter
@Setter
public class ProductResponseDto {

    private Long id;
    private String productName;
    private Integer stock;
    private Integer price;
    private String startTime;
    private String endTime;
    private String imagePath;

    public ProductResponseDto(Long id, String productName, Integer stock,
                              Integer price,String imagePath,
                              LocalDateTime startTime, LocalDateTime endTime) {
        this.id = id;
        this.productName = productName;
        this.stock = stock;
        this.price = price;
        this.imagePath = imagePath != null ? imagePath : null; // null 처리
        this.startTime = startTime != null ? startTime.toString() : null;
        this.endTime = endTime != null ? endTime.toString() : null;
    }
}
