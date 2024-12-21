package com.sparta.haengye_project.product.service;

import com.sparta.haengye_project.product.dto.ProductRequestDto;
import com.sparta.haengye_project.product.dto.ProductResponseDto;
import com.sparta.haengye_project.product.entitiy.Product;
import com.sparta.haengye_project.product.repository.ProductRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ProductService {


    private final ProductRepository productRepository;

    public ProductResponseDto createProduct(ProductRequestDto requestDto) {

        Product product = Product.fromRequestDto(requestDto);

        Product savedProduct = productRepository.save(product);

        return savedProduct.toResponseDto();

    }
}
