package com.sparta.order.repository;

import com.sparta.order.entity.Order;
import com.sparta.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByUserId(Long userId); // 사용자의 모든 주문 조회
    List<Order> findAllByStatusIn(List<OrderStatus> statuses);
    List<Order> findAllByStatus(OrderStatus status);
    Optional<Order> findByIdAndUserId(Long orderId, Long userId);
}
