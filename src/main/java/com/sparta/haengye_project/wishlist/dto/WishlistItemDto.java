package com.sparta.haengye_project.wishlist.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WishlistItemDto {

    private Long productId;
    private String productName;
    private String description;
    private int price;
    private String imagePath;
    private int quantity;

}
