package com.sparta.product.service;

import com.sparta.product.entitiy.Product;
import com.sparta.product.redis.RedisUtility;
import com.sparta.product.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CachePreloadService {

    private final ProductRepository productRepository;
    private final RedisUtility redisUtility;

    private static final String STOCK_KEY_PREFIX = "product_stock:";

    public CachePreloadService(ProductRepository productRepository, RedisUtility redisUtility) {
        this.productRepository = productRepository;
        this.redisUtility = redisUtility;
    }

    @PostConstruct
    public void preloadCache() {
        List<Product> products = productRepository.findAll(); // DB에서 모든 제품 로드
        products.forEach(product -> {
            String stockKey = STOCK_KEY_PREFIX + product.getId();
            redisUtility.saveToCache(stockKey, product.getStock()); // Redis에 저장
        });
    }
}
