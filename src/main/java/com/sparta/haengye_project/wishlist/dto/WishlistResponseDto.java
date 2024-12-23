package com.sparta.haengye_project.wishlist.dto;

import com.sparta.haengye_project.product.entitiy.Product;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor

public class WishlistResponseDto {
    private Long wishlistItemId;  // 위시리스트 항목 ID
    private Long productId;       // 상품 ID
    private String productName;   // 상품 이름
    private int quantity;


    public WishlistResponseDto(Long wishlistItemId, Long productId, String productName, int quantity) {
        this.wishlistItemId = wishlistItemId;
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
    }



    public static WishlistResponseDto createFrom(Product product, int quantity) {
        return new WishlistResponseDto(
               null,   // wishlistItemId와 동일
                product.getId(),
                product.getProductName(),
                quantity
        );
    }

}
