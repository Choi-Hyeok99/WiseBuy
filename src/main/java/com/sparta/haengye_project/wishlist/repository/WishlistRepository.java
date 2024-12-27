package com.sparta.haengye_project.wishlist.repository;

import com.sparta.haengye_project.user.entity.User;
import com.sparta.haengye_project.wishlist.entity.WishListItem;
import com.sparta.haengye_project.wishlist.entity.Wishlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WishlistRepository extends JpaRepository<Wishlist, Long> {

    Optional<Wishlist> findByUserId(Long userId);

    @Query("SELECT w FROM WishListItem w JOIN FETCH w.product WHERE w.wishlist = :wishlist")
    List<WishListItem> findByWishlistWithProduct(@Param("wishlist") Wishlist wishlist);


}
