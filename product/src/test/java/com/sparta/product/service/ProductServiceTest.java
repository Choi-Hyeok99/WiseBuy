package com.sparta.product.service;

import com.sparta.product.dto.ProductDetailResponseDto;
import com.sparta.product.dto.ProductRequestDto;
import com.sparta.product.dto.ProductResponseDto;
import com.sparta.product.entitiy.Product;
import com.sparta.product.entitiy.ProductInfo;
import com.sparta.product.entitiy.ProductStatus;
import com.sparta.product.entitiy.ProductType;
import com.sparta.product.exception.NotFoundException;
import com.sparta.product.redis.RedisUtility;
import com.sparta.product.repository.ProductRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * ProductService 단위 테스트.
 *
 * DB / Redis / Kafka 는 전부 Mock 으로 대체한다. 여기서 검증하고 싶은 건
 * "인프라가 잘 뜨는지"가 아니라 "재고를 깎고 되돌리는 서비스 로직이 규칙대로 동작하는지"이기 때문이다.
 * (인프라까지 함께 띄우는 테스트는 @SpringBootTest 쪽에서 따로 다룬다.)
 *
 * 선착순 재고 처리는 두 단계로 나눠져 있고, 테스트도 그 경계를 따라간다.
 *  1) reserveStock : Redis(Lua)에서 원자적으로 재고를 예약한다. 오버셀을 막는 관문.
 *  2) executeStockUpdate            : order.create 배치 컨슈머가 상품별 합산 수량으로 호출해 DB 재고를 맞춘다.
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @InjectMocks
    private ProductService productService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RedisUtility redisUtility;

    @Mock
    private HttpServletRequest request;

    // 재고/상태만 세팅한 최소 Product. 목록/상세 응답 변환에서 status, productType 를 name() 으로 읽기 때문에
    // 이 둘은 반드시 채워둬야 NPE 가 안 난다.
    private Product product(Long id, String name, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setProductName(name);
        p.setStock(stock);
        p.setUserId(1L);
        p.setStatus(ProductStatus.AVAILABLE);
        p.setProductType(ProductType.GENERAL);
        return p;
    }


    @Nested
    @DisplayName("상품 등록")
    class CreateProduct {

        @Test
        @DisplayName("저장 후 Redis 에 초기 재고를 그대로 캐싱한다")
        void cachesInitialStock() {
            ProductRequestDto dto = new ProductRequestDto(
                    "테스트 상품", 10, 1000, "설명",
                    null, null, "/images/test.jpg", "AVAILABLE", "GENERAL");

            when(request.getHeader("X-Claim-sub")).thenReturn("1");
            // save() 는 DB 가 채번한 것처럼 id 를 박아서 돌려준다.
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product saved = inv.getArgument(0);
                saved.setId(1L);
                return saved;
            });

            ProductResponseDto result = productService.createProduct(dto, request);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getProductName()).isEqualTo("테스트 상품");
            // 등록 직후 재고 조회가 전부 Redis 로 가도록, 저장 시점에 "product_stock:{id}" 키를 미리 채워야 한다.
            verify(redisUtility).saveToCache("product_stock:1", 10);
        }
    }


    @Nested
    @DisplayName("상품 목록 조회")
    class GetProductList {

        @Test
        @DisplayName("id 오름차순 페이지를 응답 DTO 로 변환해 돌려준다")
        void returnsPage() {
            PageRequest pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.ASC, "id"));
            Page<Product> page = new PageImpl<>(List.of(product(1L, "상품A", 10)), pageable, 1);
            when(productRepository.findAll(pageable)).thenReturn(page);

            Page<ProductResponseDto> result = productService.getProductList(0, 5);

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getProductName()).isEqualTo("상품A");
        }

        @Test
        @DisplayName("등록된 상품이 하나도 없으면 NotFoundException")
        void throwsWhenEmpty() {
            when(productRepository.findAll(any(PageRequest.class))).thenReturn(Page.empty());

            assertThatThrownBy(() -> productService.getProductList(0, 5))
                    .isInstanceOf(NotFoundException.class);
        }
    }


    @Nested
    @DisplayName("상품 상세 조회")
    class GetProductDetails {

        @Test
        @DisplayName("존재하면 상세 DTO 반환")
        void returnsDetail() {
            Product p = product(1L, "상품A", 10);
            ProductInfo info = new ProductInfo();
            info.setPrice(1000);
            info.setDescription("설명");
            info.setImagePath("/images/a.jpg");
            p.setProductInfo(info);
            when(productRepository.findByIdWithProductInfo(1L)).thenReturn(Optional.of(p));

            ProductDetailResponseDto result = productService.getProductDetails(1L);

            assertThat(result.getStock()).isEqualTo(10);
            assertThat(result.getPrice()).isEqualTo(1000);
        }

        @Test
        @DisplayName("없으면 NotFoundException")
        void throwsWhenMissing() {
            when(productRepository.findByIdWithProductInfo(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductDetails(99L))
                    .isInstanceOf(NotFoundException.class);
        }
    }


    @Nested
    @DisplayName("상품 재고 조회 (캐시 우선)")
    class GetProductStock {

        @Test
        @DisplayName("캐시에 값이 있으면 DB 를 건드리지 않는다")
        void cacheHit() {
            when(redisUtility.getFromCache("product_stock:1", Integer.class)).thenReturn(50);

            int stock = productService.getProductStock(1L);

            assertThat(stock).isEqualTo(50);
            verifyNoInteractions(productRepository);
        }

        @Test
        @DisplayName("캐시 미스면 DB 에서 읽고 다시 캐시에 채운다 (TTL 300초)")
        void cacheMiss() {
            when(redisUtility.getFromCache("product_stock:1", Integer.class)).thenReturn(null);
            when(productRepository.findStockById(1L)).thenReturn(Optional.of(30));

            int stock = productService.getProductStock(1L);

            assertThat(stock).isEqualTo(30);
            verify(redisUtility).saveToCache("product_stock:1", 30, 300);
        }

        @Test
        @DisplayName("캐시에도 DB 에도 없으면 NotFoundException")
        void notFound() {
            when(redisUtility.getFromCache("product_stock:1", Integer.class)).thenReturn(null);
            when(productRepository.findStockById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.getProductStock(1L))
                    .isInstanceOf(NotFoundException.class);
        }
    }


    @Nested
    @DisplayName("재고 예약 - reserveStock")
    class ReserveStock {

        @Test
        @DisplayName("Lua 가 남은 재고를 양수로 돌려주면 예약 성공, 예외 없음")
        void reservesSuccessfully() {
            when(redisUtility.updateStockInRedis("1", 5)).thenReturn(45L);

            productService.reserveStock(1L, 5);

            verify(redisUtility).updateStockInRedis("1", 5);
        }

        @Test
        @DisplayName("상품 키가 Redis 에 없으면(-1) NotFoundException")
        void productMissing() {
            when(redisUtility.updateStockInRedis("1", 5)).thenReturn(-1L);

            assertThatThrownBy(() -> productService.reserveStock(1L, 5))
                    .isInstanceOf(NotFoundException.class);
        }

        @Test
        @DisplayName("남은 재고보다 많이 요청하면(-2) IllegalStateException")
        void outOfStock() {
            // 재고 부족 판정은 Lua 안에서 원자적으로 끝난다. -2 면 Redis 값은 그대로고 주문도 진행되면 안 된다.
            when(redisUtility.updateStockInRedis("1", 100)).thenReturn(-2L);

            assertThatThrownBy(() -> productService.reserveStock(1L, 100))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("재고 부족");
        }
    }


    @Nested
    @DisplayName("DB 재고 반영 - executeStockUpdate")
    class ExecuteStockUpdate {

        @Test
        @DisplayName("양수 수량이면 그만큼 DB 재고를 깎고 캐시를 갱신한다")
        void decrements() {
            Product p = product(1L, "상품A", 50);
            when(productRepository.findById(1L)).thenReturn(Optional.of(p));

            productService.executeStockUpdate(1L, 10);

            assertThat(p.getStock()).isEqualTo(40);
            verify(productRepository).save(p);
            verify(redisUtility).saveToCache("product_stock:1", 40);
        }

        @Test
        @DisplayName("음수 수량이면(취소/롤백) 그만큼 DB 재고를 되돌린다")
        void restores() {
            Product p = product(1L, "상품A", 40);
            when(productRepository.findById(1L)).thenReturn(Optional.of(p));

            productService.executeStockUpdate(1L, -10);

            assertThat(p.getStock()).isEqualTo(50);
            verify(redisUtility).saveToCache("product_stock:1", 50);
        }

        @Test
        @DisplayName("DB 기준으로 음수가 되려 하면 0으로 클램프한다 (Redis-DB가 이미 어긋난 상황)")
        void clampsInsteadOfGoingNegative() {
            // 배치 컨슈머에서 호출되므로 예외를 던지면 배치 전체가 재시도 루프에 빠진다.
            // Redis 관문을 이미 통과한 상태라 여기서는 음수만 막고 로그로 남긴다.
            Product p = product(1L, "상품A", 5);
            when(productRepository.findById(1L)).thenReturn(Optional.of(p));

            productService.executeStockUpdate(1L, 10);

            assertThat(p.getStock()).isEqualTo(0);
            verify(redisUtility).saveToCache("product_stock:1", 0);
        }

        @Test
        @DisplayName("상품이 없으면 NotFoundException")
        void productMissing() {
            when(productRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> productService.executeStockUpdate(1L, 10))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
