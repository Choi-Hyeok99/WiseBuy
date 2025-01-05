package com.sparta.wishlist.dto;


import com.sparta.common.dto.ProductResponseDto;
import com.sparta.wishlist.entity.WishListItem;
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



    public static WishlistResponseDto createFrom(ProductResponseDto product, int quantity) {
        return new WishlistResponseDto(
               null,   // wishlistItemId와 동일
                product.getId(),
                product.getProductName(),
                quantity
        );

    }
    // 정적 팩토리 메서드 추가
    public static WishlistResponseDto from(WishListItem wishlistItem, ProductResponseDto product) {
        return new WishlistResponseDto(
                wishlistItem.getId(),
                product.getId(),
                product.getProductName(),
                wishlistItem.getQuantity()
        );
    }
}
