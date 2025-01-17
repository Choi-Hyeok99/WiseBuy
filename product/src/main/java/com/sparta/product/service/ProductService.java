package com.sparta.product.service;

import com.sparta.product.dto.ProductDetailResponseDto;
import com.sparta.product.dto.ProductRequestDto;
import com.sparta.product.dto.ProductResponseDto;
import com.sparta.product.entitiy.Product;
import com.sparta.product.entitiy.ProductStatus;
import com.sparta.product.entitiy.ProductType;
import com.sparta.product.exception.ForbiddenException;
import com.sparta.product.exception.NotFoundException;
import com.sparta.product.exception.UnauthorizedException;
import com.sparta.product.repository.ProductRepository;
import com.sparta.product.redis.RedisUtility;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final RedisUtility redisUtility; // Redis 유틸리티 추가

    private static final String STOCK_KEY_PREFIX = "product_stock:";
    private static final int LOCK_TIMEOUT = 1000; // 밀리초 단위


    public ProductResponseDto createProduct(ProductRequestDto requestDto, HttpServletRequest request) {
        log.info("requestHeader" + request.getHeader("X-Claim-sub"));
        Long userId = Long.parseLong(request.getHeader("X-Claim-sub")); // 사용자 ID 값 들어감
        log.info("userId" + userId);

        if (userId == null) {
            throw new UnauthorizedException("인증되지 않은 사용자 입니다.");
        }

        Product product = Product.fromRequestDto(requestDto);
        product.setUserId(userId);

        if (product.getStartTime() == null || product.getEndTime() == null) {
            LocalDateTime now = LocalDateTime.now();
            if (product.getProductType() == ProductType.FLASH_SALE) {
                product.setStartTime(now);
                product.setEndTime(now.plusDays(1));
            } else {
                product.setStartTime(now);
                product.setEndTime(null); // 무제한 판매
            }
        }

        if (product.getProductType() == ProductType.FLASH_SALE) {
            product.setStatus(ProductStatus.UNAVAILABLE);
        } else {
            product.setStatus(ProductStatus.AVAILABLE);
        }

        Product savedProduct = productRepository.save(product);

        // Redis에 초기 재고 저장 (TTL 제거)
        String stockKey = STOCK_KEY_PREFIX + savedProduct.getId();
        redisUtility.saveToCache(stockKey, savedProduct.getStock());  // TTL 제거

        return savedProduct.toResponseDto();
    }

    public Page<ProductResponseDto> getProductList(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "id"));
        Page<Product> products = productRepository.findAll(pageable);

        if (products.isEmpty()) {
            throw new NotFoundException("등록된 상품이 없습니다.");
        }

        return products.map(Product::toResponseDto);
    }

    public ProductDetailResponseDto getProductDetails(Long productId) {
        Product product = productRepository.findByIdWithProductInfo(productId)
                                           .orElseThrow(() -> new NotFoundException("해당 상품이 존재하지 않습니다. 상품 ID: " + productId));

        return product.toDetailResponseDto();
    }

    @Transactional(readOnly = true)
    public int getProductStock(Long productId) {
        String stockKey = STOCK_KEY_PREFIX + productId;

        // Redis에서 캐싱된 데이터 확인
        Integer cachedStock = redisUtility.getFromCache(stockKey, Integer.class);
        if (cachedStock != null) {
            log.info("Cache hit for product stock: " + productId);
            return cachedStock; // 캐시 데이터 반환
        }

        log.info("Cache miss for product stock: " + productId);

        // Redis 캐시에 데이터가 없으면 DB에서 조회
        int stock = productRepository.findStockById(productId)
                                     .orElseThrow(() -> new NotFoundException("해당 상품이 존재하지 않습니다. 상품 ID: " + productId));

        // Redis 캐시에 저장 (TTL 설정 가능)
        redisUtility.saveToCache(stockKey, stock, 3600); // 1시간 TTL

        return stock;
    }

    @Transactional
    public void updateStockWithDistributedLock(Long productId, int quantity) {
        String lockKey = "product_stock_lock:" + productId;

        // Redis 분산락 획득
        boolean locked = redisUtility.acquireLock(lockKey, LOCK_TIMEOUT, TimeUnit.MILLISECONDS);
        if (!locked) {
            throw new IllegalStateException("다른 요청이 재고를 수정 중입니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            // 재고 업데이트
            Product product = productRepository.findById(productId)
                                               .orElseThrow(() -> new NotFoundException("상품이 존재하지 않습니다. ID: " + productId));

            int updatedStock = product.getStock() + quantity;
            if (updatedStock < 0) {
                throw new IllegalArgumentException("재고가 부족합니다. 현재 재고: " + product.getStock());
            }

            product.setStock(updatedStock);
            productRepository.save(product);

            // Redis 캐시 갱신
            String stockKey = "product_stock:" + productId;
            redisUtility.saveToCache(stockKey, updatedStock);
        } finally {
            // 락 해제
            redisUtility.releaseLock(lockKey);
        }
    }
}