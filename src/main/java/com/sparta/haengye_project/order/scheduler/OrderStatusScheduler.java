package com.sparta.haengye_project.order.scheduler;

import com.sparta.haengye_project.order.entity.Order;
import com.sparta.haengye_project.order.entity.OrderItem;
import com.sparta.haengye_project.order.entity.OrderItemStatus;
import com.sparta.haengye_project.order.entity.OrderStatus;
import com.sparta.haengye_project.order.repository.OrderRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderStatusScheduler {

    private OrderRepository orderRepository;

    public OrderStatusScheduler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedRate = 600000) // 10분 주기로 실행
    @Transactional
    public void updateOrderStatuses() {
        List<Order> orders = orderRepository.findAllByStatusIn(List.of(OrderStatus.PENDING, OrderStatus.SHIPPED));

        for (Order order : orders) {
            LocalDateTime now = LocalDateTime.now();

            // 테스트 환경 기준: 생성 후 10분이면 배송 중, 20분이면 배송 완료
            if (now.isAfter(order.getOrderDate().plusMinutes(20))) {
                order.setStatus(OrderStatus.COMPLETED); // 배송 완료
            } else if (now.isAfter(order.getOrderDate().plusMinutes(10))) {
                order.setStatus(OrderStatus.SHIPPED); // 배송 중
            }
            // OrderItem 상태 업데이트
            for (OrderItem item : order.getOrderItems()) {
                if (order.getStatus() == OrderStatus.COMPLETED) {
                    item.setStatus(OrderItemStatus.DELIVERED); // 배송 완료
                } else if (order.getStatus() == OrderStatus.SHIPPED) {
                    item.setStatus(OrderItemStatus.SHIPPED); // 배송 중
                } else {
                    item.setStatus(OrderItemStatus.ORDERED); // 주문 접수
                }

                orderRepository.save(order);
            }
        }

        orderRepository.saveAll(orders);
        orderRepository.flush(); // 강제 동기화

    }
}
