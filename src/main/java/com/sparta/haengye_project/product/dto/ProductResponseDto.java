package com.sparta.haengye_project.product.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ProductResponseDto {

    private Long productId;
    private String productName;
    private Integer stock;
    private Integer price;
    private String startTime;
    private String endTime;

    public ProductResponseDto(Long productId, String productName, Integer stock,
                              Integer price,
                              LocalDate startTime, LocalDate endTime) {
        this.productId = productId;
        this.productName = productName;
        this.stock = stock;
        this.price = price;
        this.startTime = startTime != null ? startTime.toString() : null;
        this.endTime = endTime != null ? endTime.toString() : null;
    }
}
