package com.sparta.product.repository;


import com.sparta.product.dto.ProductResponseDto;
import com.sparta.product.entitiy.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product,Long> {
    // 재고가 있는 상품 목록 조회
    @Query("SELECT p FROM Product p WHERE p.stock > 0")
    Page<Product> findAvailableProducts(Pageable pageable);

    // 상품 상세 정보 조회 (ProductInfo 포함)
    @Query("SELECT p FROM Product p JOIN FETCH p.productInfo WHERE p.id = :productId")
    Optional<Product> findByIdWithProductInfo(@Param("productId") Long productId);


    // **재고(stock)만 조회하는 최적화된 쿼리**
    @Query("SELECT p.stock FROM Product p WHERE p.id = :productId")
    Optional<Integer> findStockById(@Param("productId") Long productId);

}
