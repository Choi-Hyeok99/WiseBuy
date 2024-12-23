package com.sparta.haengye_project.order.entity;

import com.sparta.haengye_project.user.entity.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "orders") // 예약어를 피하기 위해 테이블 이름 변경
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 주문 번호

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user+id",nullable = false)
    private User user; // 회원과 연관 관계 ( N : 1 )

    @OneToMany(mappedBy = "order",cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>(); // 주문 항목과 연관 관계 ( 1 : N )

    @Column(nullable = false)
    private LocalDateTime orderDate; // 주문날짜

    @Column(nullable = true)
    private LocalDateTime cancelDate; // 주문 취소일

    @Column(nullable = true)
    private LocalDateTime deliveryDate; // 주문 완료(배송 완료)일

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING; // 주문 상태 (ENUM: PENDING, CANCELLED, COMPLETED 등)

    @Column(nullable = false)
    private int totalAmount; // 주문 총 금액

    @Column(nullable = false)
    private String shippingAddress; // 배송 주소






}
