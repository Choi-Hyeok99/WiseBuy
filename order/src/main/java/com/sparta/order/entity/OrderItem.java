package com.sparta.order.entity;


import com.sparta.common.dto.ProductResponseDto;
import com.sparta.common.dto.WishlistItemDto;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class OrderItem {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 주문 항목 번호

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order; // 주문과 연관 관계 (N:1)

    @Column(name = "product_id", nullable = false)
    private Long productId;


    @Column(nullable = false)
    private int quantity; // 주문 수량

    @Column(nullable = false)
    private int price; // 상품 가격 (단가 * 수량)

    @Column(nullable = true)
    private LocalDateTime deliveryDate; // 배송 완료일

    @Column(nullable = true)
    private LocalDateTime returnDate; // 반품 완료일

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderItemStatus status = OrderItemStatus.ORDERED;



}
