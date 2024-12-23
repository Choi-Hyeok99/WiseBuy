package com.sparta.haengye_project.wishlist.service;

import com.sparta.haengye_project.product.entitiy.Product;
import com.sparta.haengye_project.product.entitiy.ProductInfo;
import com.sparta.haengye_project.product.repository.ProductRepository;
import com.sparta.haengye_project.user.entity.User;
import com.sparta.haengye_project.wishlist.dto.WishlistItemDto;
import com.sparta.haengye_project.wishlist.dto.WishlistRequestDto;
import com.sparta.haengye_project.wishlist.dto.WishlistResponseDto;
import com.sparta.haengye_project.wishlist.dto.WishlistUpdateRequestDto;
import com.sparta.haengye_project.wishlist.entity.WishListItem;
import com.sparta.haengye_project.wishlist.entity.Wishlist;
import com.sparta.haengye_project.wishlist.repository.WishListItemRepository;
import com.sparta.haengye_project.wishlist.repository.WishlistRepository;
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
    private final ProductRepository productRepository;
    private final WishListItemRepository wishListItemRepository;

    public WishlistResponseDto addToWishlist(WishlistRequestDto requestDto, User user) {
        System.out.println("User ID: " + user.getId());
        System.out.println("Product ID from request: " + requestDto.getProductId());


        // 유저의 위시리스트 가져오기 ( 없으면 생성 )
        Wishlist wishlist = wishlistRepository.findByUser(user)
                                              .orElseGet(() -> {
                                                  System.out.println("Wishlist not found for user. Creating new wishlist.");
                                                  return createNewWishList(user);
                                              });

        System.out.println("Wishlist ID: " + wishlist.getId());

        // 등록한 상품 찾기
        Product product = productRepository.findById(requestDto.getProductId())
                                           .orElseThrow(() -> {
                                               System.out.println("Product not found with ID: " + requestDto.getProductId());
                                               return new IllegalArgumentException("해당 제품을 찾을 수 없습니다. ID: " + requestDto.getProductId());
                                           });

        System.out.println("Found Product: " + product.getProductName());

        // 기존 위시리스트 항목 확인
        WishListItem existingItem = wishListItemRepository.findByWishlistAndProduct(wishlist, product)
                                                          .orElse(null);

        if (existingItem != null) {
            System.out.println("Existing wishlist item found. Updating quantity.");
            existingItem.setQuantity(existingItem.getQuantity() + requestDto.getQuantity());
            wishListItemRepository.save(existingItem);
            System.out.println("Updated quantity for wishlist item: " + existingItem.getQuantity());
        } else {
            System.out.println("No existing wishlist item found. Adding new item.");
            WishListItem newItem = new WishListItem();
            newItem.setWishlist(wishlist);
            newItem.setProduct(product);
            newItem.setQuantity(requestDto.getQuantity());
            wishListItemRepository.save(newItem);
            System.out.println("New wishlist item saved. Product ID: " + newItem.getProduct().getId());
        }

        System.out.println("Returning WishlistResponseDto.");
        return WishlistResponseDto.createFrom(product, requestDto.getQuantity());
    }

    @Transactional(readOnly = true)
    public List<WishlistItemDto> getWishlist(User user) {
        // 유저의 위시리스트 찾기
        Wishlist wishlist = wishlistRepository.findByUser(user)
                                              .orElseThrow(() -> new IllegalArgumentException("위시리스트가 없습니다."));
        // WishlistItem을 WishlistItemDto로 변환
        return wishlist.getItems().stream()
                       .map(item -> {
                           Product product = item.getProduct();
                           ProductInfo productInfo = product.getProductInfo(); // Product와 연관된 ProductInfo 가져오기

                           return new WishlistItemDto(
                                   product.getId(),
                                   product.getProductName(),
                                   productInfo.getDescription(),
                                   productInfo.getPrice(),
                                   productInfo.getImagePath(),
                                   item.getQuantity()
                           );
                       })
                       .toList();
    }
    @Transactional
    public WishlistResponseDto updateWishlistItem(Long id, WishlistUpdateRequestDto updateRequestDto, User user) {
        // 유저의 위시리스트 가져오기
        Wishlist wishlist = wishlistRepository.findByUser(user)
                                              .orElseThrow(() -> new IllegalArgumentException("해당 유저의 위시리스트가 없습니다."));

        // 위시리스트 항목 찾기 (위시리스트와 연결된 항목인지 확인)
        WishListItem wishlistItem = wishListItemRepository.findByIdAndWishlist(id, wishlist)
                                                          .orElseThrow(() -> new IllegalArgumentException("해당 위시리스트 항목이 없습니다."));

        // 수량 수정
        wishlistItem.setQuantity(updateRequestDto.getQuantity());

        // 저장
        wishListItemRepository.save(wishlistItem);

        // 응답 DTO 생성 및 반환
        return new WishlistResponseDto(
                wishlistItem.getId(),
                wishlistItem.getProduct().getId(),
                wishlistItem.getProduct().getProductName(),
                wishlistItem.getQuantity()
        );
    }

    @Transactional
    public void deleteWishlistItem(Long id, User user) {
        // 위시리스트 항목 조회 (사용자와 연관된 항목인지 확인)
        WishListItem wishlistItem = wishListItemRepository.findByIdAndWishlist(id, user.getWishlist())
                                                          .orElseThrow(() -> new IllegalArgumentException("해당 위시리스트 항목이 없습니다."));

        // 위시리스트 항목 삭제
        wishListItemRepository.delete(wishlistItem);
    }

    // 위시리스트 생성 메서드
    private Wishlist createNewWishList(User user) {
        Wishlist wishlist = new Wishlist();
        wishlist.setUser(user); // 유저와 연결
        return wishlistRepository.save(wishlist); // DB에 저장
    }

}
