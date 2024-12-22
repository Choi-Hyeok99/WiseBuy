package com.sparta.haengye_project.wishlist.dto;

import com.sparta.haengye_project.product.entitiy.Product;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WishlistResponseDto {
    private Long wishlistItemId;  // 위시리스트 항목 ID
    private Long productId;       // 상품 ID
    private String productName;   // 상품 이름
    private int quantity;

    public static WishlistResponseDto createFrom(Product product, int quantity) {
        return new WishlistResponseDto(
               null,   // wishlistItemId와 동일
                product.getId(),
                product.getProductName(),
                quantity
        );
    }
}
