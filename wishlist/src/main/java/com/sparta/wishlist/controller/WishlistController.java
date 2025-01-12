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

// 위시리스트 등록
    @PostMapping("/wishlist")
    public ResponseEntity<WishlistResponseDto> addToWishlist(@RequestBody WishlistRequestDto requestDto, HttpServletRequest request) {
        // 위시리스트에 추가
        WishlistResponseDto responseDto = wishlistService.addToWishlist(requestDto, request);
        // 성공 응답 반환
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }


    // 위시리스트 조회
    @GetMapping("/wishlist")
    public ResponseEntity<List<WishlistItemDto>> getWishlist(HttpServletRequest request) {
        log.info("Received request to get wishlist.");

        // JWT 헤더에서 사용자 ID 가져오기
        Long userId = Long.parseLong(request.getHeader("X-Claim-sub")); // id 값 들어감
        log.info("Authenticated userId: {}", userId);

        // 서비스 호출하여 위시리스트 항목 가져오기
        List<WishlistItemDto> wishlistItems = wishlistService.getWishlist(userId);

        return ResponseEntity.ok(wishlistItems);
    }

    @PutMapping("/wishlist/{id}")
    public ResponseEntity<WishlistResponseDto> updateWishlistItem(
            @PathVariable Long id,
            @RequestBody WishlistUpdateRequestDto updateRequestDto,
            HttpServletRequest request
    ) {
        log.info("Received request to get wishlist.");

        // JWT 헤더에서 사용자 ID 가져오기
        Long userId = Long.parseLong(request.getHeader("X-Claim-sub")); // id 값 들어감


        // 서비스 호출하여 수정 처리
        WishlistResponseDto updatedItem = wishlistService.updateWishlistItem(id, updateRequestDto, userId);

        log.info("Successfully updated wishlist item: {}", updatedItem);
        return ResponseEntity.ok(updatedItem);
    }
    @DeleteMapping("/wishlist/{id}")
    public ResponseEntity<String> deleteWishlistItem(@PathVariable Long id, HttpServletRequest request){
        // SecurityContext에서 인증된 사용자 가져오기

        Long userId = Long.parseLong(request.getHeader("X-Claim-sub")); // id 값 들어감

        // 서비스 호출하여 삭제 처리
        wishlistService.deleteWishlistItem(id, userId);


        log.info("Successfully deleted wishlist item with id: {}", id);
        return ResponseEntity.ok("Wishlist item deleted successfully.");
    }
}
