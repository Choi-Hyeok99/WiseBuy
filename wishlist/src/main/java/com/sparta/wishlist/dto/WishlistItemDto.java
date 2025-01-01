package com.sparta.wishlist.dto;

import com.sparta.common.dto.ProductResponseDto;
import com.sparta.wishlist.entity.WishListItem;
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

    public static WishlistItemDto from(WishListItem wishListItem, ProductResponseDto product) {
        return new WishlistItemDto(
                product.getId(),
                product.getProductName(),
                product.getPrice(),
                product.getImagePath(),
                wishListItem.getQuantity()
        );
    }

}
