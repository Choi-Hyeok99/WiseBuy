package com.sparta.haengye_project.wishlist.controller;

import com.sparta.haengye_project.security.UserDetailsImpl;
import com.sparta.haengye_project.user.entity.User;
import com.sparta.haengye_project.wishlist.dto.WishlistItemDto;
import com.sparta.haengye_project.wishlist.dto.WishlistRequestDto;
import com.sparta.haengye_project.wishlist.dto.WishlistResponseDto;
import com.sparta.haengye_project.wishlist.dto.WishlistUpdateRequestDto;
import com.sparta.haengye_project.wishlist.service.WishlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
public class WishlistController {

    private final WishlistService wishlistService;

    // 위시리스트 등록
// 위시리스트 등록
    @PostMapping("/wishlist")
    public ResponseEntity<WishlistResponseDto> addToWishlist(@RequestBody WishlistRequestDto requestDto) {
        System.out.println("Received request to add to wishlist: " + requestDto);

        // SecurityContext에서 인증 정보 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 인증 정보 유효성 검증
        if (authentication == null || !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            throw new IllegalStateException("인증된 사용자 정보가 없습니다.");
        }

        // 인증된 사용자 정보 가져오기
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userDetails.getUser();  // User 객체 가져오기
        System.out.println("Authenticated user: " + user.getEmail());

        // 위시리스트에 추가
        WishlistResponseDto responseDto = wishlistService.addToWishlist(requestDto, user);

        // 성공 응답 반환
        System.out.println("Successfully added to wishlist. Response: " + responseDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
    }


    // 위시리스트 조회
    @GetMapping("/wishlist")
    public ResponseEntity<List<WishlistItemDto>> getWishlist() {
        log.info("Received request to get wishlist.");

        // SecurityContext에서 인증 정보 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // 인증 정보 유효성 검증
        if (authentication == null || !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            throw new IllegalStateException("인증된 사용자 정보가 없습니다.");
        }

        // 인증된 사용자 정보 가져오기
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userDetails.getUser();  // User 객체 가져오기
        log.info("Authenticated user: {}", user.getEmail());

        // 서비스 호출하여 위시리스트 항목 가져오기
        List<WishlistItemDto> wishlistItems = wishlistService.getWishlist(user);

        return ResponseEntity.ok(wishlistItems);
    }

    @PutMapping("/wishlist/{id}")
    public ResponseEntity<WishlistResponseDto> updateWishlistItem(
            @PathVariable Long id,
            @RequestBody WishlistUpdateRequestDto updateRequestDto
    ) {
        // 로그인 상태 확인
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl)) {
            throw new IllegalStateException("인증된 사용자 정보가 없습니다.");
        }

        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userDetails.getUser();

        // 서비스 호출하여 수정 처리
        WishlistResponseDto updatedItem = wishlistService.updateWishlistItem(id, updateRequestDto, user);

        log.info("Successfully updated wishlist item: {}", updatedItem);
        return ResponseEntity.ok(updatedItem);
    }
}
