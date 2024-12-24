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

    private final OrderRepository orderRepository;

    public OrderStatusScheduler(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Scheduled(fixedRate = 300000) // 5분 주기로 실행
    @Transactional
    public void updateOrderStatuses() {
        // 상태가 PENDING 또는 SHIPPED인 주문을 조회
        List<Order> orders = orderRepository.findAllByStatusIn(List.of(OrderStatus.PENDING, OrderStatus.SHIPPED));

        for (Order order : orders) {
            LocalDateTime now = LocalDateTime.now();

            // 주문 상태 업데이트
            if (now.isAfter(order.getOrderDate().plusMinutes(10))) {
                order.setStatus(OrderStatus.COMPLETED); // 배송 완료
            } else if (now.isAfter(order.getOrderDate().plusMinutes(5))) {
                order.setStatus(OrderStatus.SHIPPED); // 배송 중
            }

            // 주문 항목 상태 업데이트
            for (OrderItem item : order.getOrderItems()) {
                if (order.getStatus() == OrderStatus.COMPLETED) {
                    item.setStatus(OrderItemStatus.DELIVERED); // 배송 완료
                } else if (order.getStatus() == OrderStatus.SHIPPED) {
                    item.setStatus(OrderItemStatus.SHIPPED); // 배송 중
                } else {
                    item.setStatus(OrderItemStatus.ORDERED); // 주문 접수
                }
            }
        }

        // 모든 변경 사항 저장
        orderRepository.saveAll(orders);
        orderRepository.flush(); // 강제 동기화
    }

    @Scheduled(fixedRate = 400000, initialDelay = 30000) // 5분 주기로 실행
    @Transactional
    public void processReturns() {
        // RETURN_REQUESTED 상태의 주문 조회
        List<Order> returnRequestedOrders = orderRepository.findAllByStatus(OrderStatus.RETURN_REQUESTED);

        for (Order order : returnRequestedOrders) {
            LocalDateTime now = LocalDateTime.now();

            // 반품 처리 가능 시간 확인 (반품 요청 후 10분 전)
            if (now.isAfter(order.getDeliveryDate().plusMinutes(10))) {
                // 상태 변경
                order.setStatus(OrderStatus.RETURN);

                // 주문 항목 상태 변경 및 재고 복구
                for (OrderItem item : order.getOrderItems()) {
                    item.setStatus(OrderItemStatus.RETURNED);
                    // 재고 복구
                    item.getProduct().setStock(item.getProduct().getStock() + item.getQuantity());
                }
            }
        }

        // 모든 변경 사항 저장
        orderRepository.saveAll(returnRequestedOrders);
        orderRepository.flush(); // 강제 동기화
    }
}