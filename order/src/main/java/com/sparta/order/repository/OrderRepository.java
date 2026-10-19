package com.sparta.order.repository;

import com.sparta.order.entity.Order;
import com.sparta.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByUserId(Long userId); // 사용자의 모든 주문 조회
    List<Order> findAllByStatusIn(List<OrderStatus> statuses);
    List<Order> findAllByStatus(OrderStatus status);
    Optional<Order> findByIdAndUserId(Long orderId, Long userId);

    // 만료 스케줄러용. 엔티티 전체가 아니라 id만 가져와 주문 단위 트랜잭션으로 하나씩 처리한다.
    @Query("select o.id from Order o where o.status in :statuses and o.orderDate < :before")
    List<Long> findIdsByStatusInAndOrderDateBefore(@Param("statuses") List<OrderStatus> statuses,
                                                   @Param("before") LocalDateTime before);
}
