package com.sparta.wishlist.service;


import com.sparta.common.dto.ProductResponseDto;
import com.sparta.wishlist.client.ProductClient;
import com.sparta.wishlist.dto.WishlistItemDto;
import com.sparta.wishlist.dto.WishlistRequestDto;
import com.sparta.wishlist.dto.WishlistResponseDto;
import com.sparta.wishlist.dto.WishlistUpdateRequestDto;
import com.sparta.wishlist.entity.WishListItem;
import com.sparta.wishlist.entity.Wishlist;
import com.sparta.wishlist.repository.WishListItemRepository;
import com.sparta.wishlist.repository.WishlistRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class WishlistService {

    private final WishlistRepository wishlistRepository;
    private final WishListItemRepository wishListItemRepository;
    private final ProductClient productClient;

    @Transactional
    public WishlistResponseDto addToWishlist(WishlistRequestDto requestDto, HttpServletRequest request) {
        // JWT 헤더에서 사용자 ID 가져오기
        Long userId = Long.parseLong(request.getHeader("X-Claim-sub"));

        // 제품 정보 가져오기
        ProductResponseDto product = productClient.getProductById(requestDto.getProductId());

        // 위시리스트와 항목 조회 또는 생성
        Wishlist wishlist = wishlistRepository.findByUserId(userId)
                                              .orElseGet(() -> createNewWishlist(userId));

        WishListItem wishlistItem = wishListItemRepository.findByWishlistAndProductId(wishlist, requestDto.getProductId())
                                                          .orElseGet(() -> {
                                                              WishListItem newItem = new WishListItem(wishlist, requestDto.getProductId(), 0);
                                                              wishlist.addItem(newItem); // Wishlist의 항목 리스트 관리
                                                              return newItem;
                                                          });

        // 수량 업데이트
        wishlistItem.addQuantity(requestDto.getQuantity());
        wishListItemRepository.save(wishlistItem);

        return WishlistResponseDto.createFrom(product, wishlistItem.getQuantity());
    }

    @Transactional(readOnly = true)
    public List<WishlistItemDto> getWishlist(Long userId) {
        // 유저의 위시리스트 찾기
        Wishlist wishlist = wishlistRepository.findByUserId(userId)
                                              .orElseThrow(() -> new IllegalArgumentException("위시리스트가 없습니다."));

        // WishlistItem -> WishlistItemDto로 변환
        return wishlist.getItems().stream()
                       .map(item -> {
                           // Feign Client를 통해 product-service에서 상품 정보 가져오기
                           ProductResponseDto product = productClient.getProductById(item.getProductId());
                           return WishlistItemDto.from(item,product);
                       })
                       .toList();
    }


    @Transactional
    public WishlistResponseDto updateWishlistItem(Long id, WishlistUpdateRequestDto updateRequestDto, Long userId) {
        // 유저의 위시리스트 가져오기
        Wishlist wishlist = wishlistRepository.findByUserId(userId)
                                              .orElseThrow(() -> new IllegalArgumentException("해당 유저의 위시리스트가 없습니다."));

        // 위시리스트 항목 찾기 (위시리스트와 연결된 항목인지 확인)
        WishListItem wishlistItem = wishListItemRepository.findByIdAndWishlist(id, wishlist)
                                                          .orElseThrow(() -> new IllegalArgumentException("해당 위시리스트 항목이 없습니다."));

        // 수량 수정
        wishlistItem.setQuantity(updateRequestDto.getQuantity());

        // 저장
        wishListItemRepository.save(wishlistItem);

        // Product 정보 가져오기 (FeignClient 사용)
        ProductResponseDto product = productClient.getProductById(wishlistItem.getProductId());

        // 응답 DTO 생성 및 반환
        return WishlistResponseDto.from(wishlistItem, product);
    }

    @Transactional
    public void deleteWishlistItem(Long id, Long userId) {
        // 유저의 위시리스트 가져오기
        Wishlist wishlist = wishlistRepository.findByUserId(userId)
                                              .orElseThrow(() -> new IllegalArgumentException("해당 유저의 위시리스트가 없습니다."));

        // 위시리스트 항목 찾기 (위시리스트와 연결된 항목인지 확인)
        WishListItem wishlistItem = wishListItemRepository.findByIdAndWishlist(id, wishlist)
                                                          .orElseThrow(() -> new IllegalArgumentException("해당 위시리스트 항목이 없습니다."));

        // 위시리스트 항목 삭제
        wishListItemRepository.delete(wishlistItem);
    }

    // 위시리스트 생성 메서드
    private Wishlist createNewWishlist(Long userId) {
        Wishlist wishlist = new Wishlist();
        wishlist.setUserId(userId); // 유저와 연결
        return wishlistRepository.save(wishlist); // DB에 저장
    }
}
