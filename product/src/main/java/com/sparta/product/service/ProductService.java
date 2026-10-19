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

        // 캐시 미스일 때만 채운다. 무조건 SET하면 그 사이 reserveStock이 원자적으로 갱신해둔
        // 실시간 재고 예약 카운터를 이 DB 스냅샷이 덮어써버릴 수 있다(SET NX로 방지).
        redisUtility.saveToCacheIfAbsent(stockKey, stock, 300); // 300초 TTL

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
     *
     * orderId가 오면 주문별 예약 기록을 같이 남긴다. 주문 생성이 롤백되면 order가 "예약을 시도한 항목 전부"에
     * 대해 해제를 보내는데, 실제로 예약된 것만 되돌리고 늦게 도착한 예약은 막기 위해서다(RedisUtility 참고).
     *
     * 예전엔 @Transactional이 붙어 있어서 Redis만 쓰는데도 DB 커넥션을 먼저 잡았다. DB 풀이 고갈되면
     * order는 3초 타임아웃으로 "실패"라고 보고 롤백했는데, product는 몇 초 뒤 커넥션을 얻어 Redis를 실제로
     * 차감해 재고가 샜다(SAGA 정합성 검증 중 발견). 재고 관문은 DB와 무관해야 하므로 제거했다.
     */
    public void reserveStock(Long productId, int quantity, Long orderId) {
        Long remaining = orderId == null
                ? redisUtility.updateStockInRedis(String.valueOf(productId), quantity)
                : redisUtility.reserveStockInRedis(String.valueOf(productId), quantity, orderId);
        if (remaining == -1) {
            throw new NotFoundException("상품이 존재하지 않습니다.");
        }
        if (remaining == -2) {
            throw new IllegalStateException("재고 부족으로 주문이 취소되었습니다.");
        }
        if (remaining == -3) {
            throw new IllegalStateException("이미 취소된 주문의 재고 예약 요청입니다.");
        }
    }

    public void reserveStock(Long productId, int quantity) {
        reserveStock(productId, quantity, null);
    }

    /**
     * order.create 배치 컨슈머가 호출하는 DB 재고 반영. 상품별로 합산된 수량(양수=차감, 음수=복구)을 받는다.
     * Redis(예약)는 이미 반영된 상태이고, 여기서는 DB만 그 결과에 맞춘다.
     *
     * 예전엔 여기서 DB로 계산한 값을 Redis 캐시(product_stock:{id})에도 다시 썼는데, 그 키는
     * reserveStock/rollbackOrderEvent가 실시간으로 관리하는 원자 예약 카운터와 같은 키였다. 이 비동기
     * 쓰기가 그 카운터를 덮어써 되돌리면서 지속적인 동시 주문 상황에서 오버셀이 발생했다(재검증 완료).
     * Redis 카운터는 이미 정확하므로 여기서 다시 쓸 필요가 없어 그 호출을 제거한다.
     *
     * 원자적 상대 UPDATE(stock = stock - ?)로 반영하고, 음수가 돼도 0으로 자르지 않는다.
     * 차감(order.create)과 복구(stock.rollback)는 서로 다른 토픽이라 도착 순서가 보장되지 않는다. 결제 실패·만료로
     * 풀린 재고가 재판매되면 "재판매 차감"이 "이전 복구"보다 먼저 와서 DB가 잠깐 음수가 될 수 있는데, 예전엔 이걸
     * 0으로 클램프해서 차이를 버렸고 모든 이벤트가 반영된 뒤에도 DB가 Redis보다 많아졌다(SAGA 정합성 검증 중 발견:
     * 재고 300 / 차감 356건 → DB가 57 많음). 또 findById → setStock → save는 두 컨슈머가 같은 행을 동시에
     * 갱신하면 lost update가 난다. 상대 UPDATE는 순서와 무관하게 최종값이 정확하다.
     * DB 재고는 판매 관문이 아니라 Redis를 뒤따라가는 기록이다(관문은 Redis Lua).
     */
    @Transactional
    public void executeStockUpdate(Long productId, int quantity) {
        if (productRepository.decreaseStock(productId, quantity) == 0) {
            throw new NotFoundException("상품이 존재하지 않습니다.");
        }
    }
}