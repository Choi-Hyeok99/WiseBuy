package com.sparta.order.client;

import com.sparta.common.dto.WishlistItemDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(name = "wishlist-service")
public interface WishlistClient {
    @GetMapping("/wishlist")
    List<WishlistItemDto> getWishlist(@RequestHeader("X-Claim-sub") Long userId);
}