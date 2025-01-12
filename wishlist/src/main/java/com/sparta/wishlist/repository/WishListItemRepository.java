package com.sparta.wishlist.repository;



import com.sparta.wishlist.entity.WishListItem;
import com.sparta.wishlist.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WishListItemRepository extends JpaRepository<WishListItem, Long> {
    Optional<WishListItem> findByWishlistAndProductId(Wishlist wishlist, Long productId);
    Optional<WishListItem> findByIdAndWishlist(Long id, Wishlist wishlist);


}
