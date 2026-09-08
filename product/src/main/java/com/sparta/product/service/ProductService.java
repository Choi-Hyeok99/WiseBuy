package com.sparta.product.service;

import com.sparta.product.dto.ProductDetailResponseDto;
import com.sparta.product.dto.ProductRequestDto;
import com.sparta.product.dto.ProductResponseDto;
import com.sparta.product.entitiy.Product;
import com.sparta.product.entitiy.ProductStatus;
import com.sparta.product.entitiy.ProductType;
import com.sparta.product.exception.NotFoundException;
import com.sparta.product.exception.UnauthorizedException;
import com.sparta.product.redis.RedisUtility;
import com.sparta.product.repository.ProductRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;


@Service
@AllArgsConstructor
@Slf4j
public class ProductService {

    private final ProductRepository productRepository;
    private final RedisUtility redisUtility; // Redis 유틸리티 추가
    private static final String STOCK_KEY_PREFIX = "product_stock:";


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
        redisUtility.saveToCache(stockKey, stock, 300); // 300초 TTL

        return stock;
    }

    /**
     * 재고 예약 (동기). Redis Lua로 "조회 → 부족 판정 → 차감"을 원자적으로 실행한다.
     * 선착순 판매에서 오버셀을 막는 관문. 동시 요청이 몰려도 Redis가 Lua를 단일 스레드로
     * 통째 실행하므로 race condition이 없다.
     *
     * quantity 규약: 양수 = 차감(주문), 음수 = 복구(취소). DB 반영은 여기서 하지 않고
     * order.create 이벤트를 받은 배치 컨슈머(ProductConsumerService)가 처리한다.
     * 재고 부족/상품 없음은 예외를 그대로 던져 주문이 실패하게 둔다(재시도 의미 없음).
     */
    @Transactional
    public void reserveStock(Long productId, int quantity) {
        Long remaining = redisUtility.updateStockInRedis(String.valueOf(productId), quantity);
        if (remaining == -1) {
            throw new NotFoundException("상품이 존재하지 않습니다.");
        }
        if (remaining == -2) {
            throw new IllegalStateException("재고 부족으로 주문이 취소되었습니다.");
        }
    }

    /**
     * order.create 배치 컨슈머가 호출하는 DB 재고 반영. 상품별로 합산된 수량(양수=차감, 음수=복구)을 받는다.
     * Redis(예약)는 이미 반영된 상태이고, 여기서 DB와 캐시를 그 결과에 맞춘다.
     */
    @Transactional
    public void executeStockUpdate(Long productId, int quantity) {
        Product product = productRepository.findById(productId)
                                           .orElseThrow(() -> new NotFoundException("상품이 존재하지 않습니다."));

        int updatedStock = product.getStock() - quantity;
        if (updatedStock < 0) {
            // Redis 관문을 통과했는데 DB 기준으로 음수라면 Redis-DB가 이미 어긋난 상황. 로그만 남기고 0으로 막는다.
            log.warn("DB 재고가 음수가 되려 함 - 상품 ID: {}, 현재: {}, 반영 시도: {}. 0으로 클램프.",
                    productId, product.getStock(), quantity);
            updatedStock = 0;
        }

        product.setStock(updatedStock);
        productRepository.save(product);

        redisUtility.saveToCache(STOCK_KEY_PREFIX + productId, updatedStock);
    }
}