package com.sparta.haengye_project.order.repository;

import com.sparta.haengye_project.order.entity.Order;
import com.sparta.haengye_project.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findAllByUser(User user); // 사용자의 모든 주문 조회
}
