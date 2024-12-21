package com.sparta.haengye_project.product.repository;

import com.sparta.haengye_project.product.dto.ProductResponseDto;
import com.sparta.haengye_project.product.entitiy.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product,Long> {


    @Query("SELECT new com.sparta.haengye_project.product.dto.ProductResponseDto(p.id, p.productName, p.stock, pi.price, pi.imagePath, p.startTime, p.endTime) " +
            "FROM Product p " +
            "JOIN ProductInfo pi ON p.id = pi.product.id")
    List<ProductResponseDto> findAllWithProductInfo();

}
