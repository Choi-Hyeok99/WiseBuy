package com.sparta.haengye_project.wishlist.repository;

import com.sparta.haengye_project.product.entitiy.Product;
import com.sparta.haengye_project.wishlist.entity.WishListItem;
import com.sparta.haengye_project.wishlist.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WishListItemRepository extends JpaRepository<WishListItem , Long> {
    Optional<WishListItem> findByWishlistAndProduct(Wishlist wishlist, Product product);
}
