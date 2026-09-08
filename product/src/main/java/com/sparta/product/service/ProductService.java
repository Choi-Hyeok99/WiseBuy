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
     * 재고 "예약" (동기). Redis에서 Lua 스크립트로 원자적으로 재고를 차감한다.
     * 선착순 판매에서 오버셀을 막는 진짜 관문이 여기다. 여러 요청이 동시에 들어와도
     * get→비교→차감이 Redis 단일 스레드로 한 덩어리로 실행되므로 race condition이 없다.
     *
     * quantity 규약: 양수 = 차감(주문), 음수 = 복구(주문 취소). Lua는 DECRBY이므로
     * DECRBY key -n = +n 으로 복구도 자연스럽게 처리된다.
     *
     * DB 반영은 여기서 하지 않는다. 주문 서비스가 발행하는 order.create 이벤트를
     * product 쪽 배치 컨슈머(ProductConsumerService.consumeOrderEvents)가 묶어서 처리한다.
     * (예전엔 이 메서드가 stock.update 이벤트를 따로 또 발행했는데, 그 컨슈머가 배치/단건
     *  설정 불일치로 실제로 동작하지 않아 Redis와 DB가 어긋나고 있었다. 경로를 하나로 정리함.)
     *
     * @Retry는 뗐다. 예전엔 이 메서드에 @Retry(retry-exceptions=IllegalStateException, fallback=로그만)이
     * 붙어 있어서, "재고 부족"(IllegalStateException)이 5번 재시도된 뒤 fallback에서 삼켜져
     * 예외가 주문 흐름으로 전달되지 않았다(= 재고 부족인데 주문이 통과). Lua 연산은 이미 원자적이라
     * 재시도할 것도 없으므로, 부족/없음은 그대로 던져 주문이 실패하게 둔다.
     */
    @Transactional
    public void updateStockWithDistributedLock(Long productId, int quantity) {
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