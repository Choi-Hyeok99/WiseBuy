package com.sparta.wishlist.controller;

import com.sparta.wishlist.dto.WishlistItemDto;
import com.sparta.wishlist.dto.WishlistRequestDto;
import com.sparta.wishlist.dto.WishlistResponseDto;
import com.sparta.wishlist.dto.WishlistUpdateRequestDto;
import com.sparta.wishlist.service.WishlistService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class WishlistController {

    private final WishlistService wishlistService;

    @PostMapping("/wishlist")
    public ResponseEntity<WishlistResponseDto> addToWishlist(@RequestBody WishlistRequestDto requestDto, HttpServletRequest request) {
        WishlistResponseDto responseDto = wishlistService.addToWishlist(requestDto, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }
    @GetMapping("/wishlist")
    public ResponseEntity<List<WishlistItemDto>> getWishlist(HttpServletRequest request) {
        Long userId = Long.parseLong(request.getHeader("X-Claim-sub"));
        List<WishlistItemDto> wishlistItems = wishlistService.getWishlist(userId);

        return ResponseEntity.ok(wishlistItems);
    }
    @PutMapping("/wishlist/{id}")
    public ResponseEntity<WishlistResponseDto> updateWishlistItem(
            @PathVariable Long id,
            @RequestBody WishlistUpdateRequestDto updateRequestDto,
            HttpServletRequest request
    ) {
        Long userId = Long.parseLong(request.getHeader("X-Claim-sub")); // id 값 들어감

        WishlistResponseDto updatedItem = wishlistService.updateWishlistItem(id, updateRequestDto, userId);
        return ResponseEntity.ok(updatedItem);
    }
    @DeleteMapping("/wishlist/{id}")
    public ResponseEntity<String> deleteWishlistItem(@PathVariable Long id, HttpServletRequest request){
        Long userId = Long.parseLong(request.getHeader("X-Claim-sub")); // id 값 들어감
        wishlistService.deleteWishlistItem(id, userId);

        return ResponseEntity.ok("Wishlist item deleted successfully.");
    }
}
