package com.sparta.order.entity;


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

     @Column(name = "user_id", nullable = false)
     private Long userId;

    @OneToMany(mappedBy = "order",cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>(); // 주문 항목과 연관 관계 ( 1 : N )

    @Column(nullable = false)
    private LocalDateTime orderDate; // 주문날짜

    @Column(nullable = true)
    private LocalDateTime cancelDate; // 주문 취소일

    @Column(nullable = true)
    private LocalDateTime deliveryDate; // 주문 완료(배송 완료)일

    // Hibernate 6은 MySQL에서 STRING enum을 ENUM('PENDING',...) 컬럼으로 만든다. 그런데 ddl-auto: update는
    // 기존 ENUM 컬럼에 값을 추가해주지 않아서 EXPIRED 추가 후 "Data truncated for column 'status'"로 저장이 실패했다.
    // 상태값이 늘어날 때마다 DDL이 필요 없도록 varchar로 고정한다 (기존 DB는 ALTER TABLE orders MODIFY status VARCHAR(30) NOT NULL).
    @Column(nullable = false, columnDefinition = "varchar(30)")
    @Enumerated(EnumType.STRING)
    private OrderStatus status = OrderStatus.PENDING; // 주문 상태 (ENUM: PENDING, CANCELLED, COMPLETED 등)

    @Column(nullable = false)
    private int totalAmount; // 주문 총 금액

    @Column(nullable = false)
    private String shippingAddress; // 배송 주소

    // 낙관적 락. 결제 결과 컨슈머·만료 스케줄러·사용자 취소가 같은 주문 상태를 동시에 바꾸려 하면
    // 나중에 커밋하는 쪽이 실패한다(ObjectOptimisticLockingFailureException). 실패한 쪽은 재시도 시
    // 바뀐 상태를 다시 읽고 isAwaitingPayment 가드에 걸린다 - "결제 성공 + 만료 보상"이 동시에 반영되지 않는다.
    @Version
    private long version;


}
