package com.sparta.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemDto {

    private Long productId;
    private String productName;
    private int price;
    private String imagePath;
    private int quantity;

}
