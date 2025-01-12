package com.sparta.order.scheduler;

import com.sparta.order.client.ProductClient;
import com.sparta.order.dto.StockUpdateRequestDto;
import com.sparta.order.entity.Order;
import com.sparta.order.entity.OrderItem;
import com.sparta.order.entity.OrderItemStatus;
import com.sparta.order.entity.OrderStatus;
import com.sparta.order.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderStatusScheduler {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    public OrderStatusScheduler(OrderRepository orderRepository, ProductClient productClient) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
    }

    @Scheduled(fixedRate = 300000) // 5분마다 실행
    @Transactional
    public void updateOrderStatuses() {
        log.info("Starting order status update...");
        List<Order> orders = orderRepository.findAllByStatusIn(
                List.of(OrderStatus.PAYMENT_COMPLETED, OrderStatus.SHIPPED));
        log.info("Orders to process: {}", orders.size());

        for (Order order : orders) {
            try {
                Hibernate.initialize(order.getOrderItems()); // orderItems 초기화
                if (order.getStatus() == OrderStatus.PAYMENT_COMPLETED) {
                    updateToShipped(order);
                } else if (order.getStatus() == OrderStatus.SHIPPED) {
                    updateToCompleted(order);
                }
            } catch (Exception e) {
                log.error("Failed to update order status for Order ID: {}", order.getId(), e);
            }
        }
    }

    @Transactional
    protected void updateToShipped(Order order) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(order.getOrderDate().plusMinutes(5))) {
            order.setStatus(OrderStatus.SHIPPED);
            order.setDeliveryDate(LocalDateTime.now());
            updateOrderItemsStatus(order, OrderItemStatus.SHIPPED);
            orderRepository.save(order);
            log.info("Order ID: {} updated to SHIPPED.", order.getId());
        }
    }

    @Transactional
    protected void updateToCompleted(Order order) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(order.getDeliveryDate().plusMinutes(5))) {
            order.setStatus(OrderStatus.COMPLETED);
            updateOrderItemsStatus(order, OrderItemStatus.DELIVERED);
            orderRepository.save(order);
            log.info("Order ID: {} updated to COMPLETED.", order.getId());
        }
    }

    private void updateOrderItemsStatus(Order order, OrderItemStatus status) {
        order.getOrderItems().forEach(item -> item.setStatus(status));
    }

    @Scheduled(fixedRate = 30000, initialDelay = 30000) // 30초 주기로 실행
    @Transactional
    public void processReturns() {
        log.info("Processing return requests...");
        LocalDateTime now = LocalDateTime.now();

        List<Order> completedOrders = orderRepository.findAllByStatus(OrderStatus.COMPLETED);
        List<Order> returnRequestedOrders = orderRepository.findAllByStatus(OrderStatus.RETURN_REQUESTED);

        processCompletedOrders(completedOrders, now);
        processReturnRequestedOrders(returnRequestedOrders, now);

        completedOrders.addAll(returnRequestedOrders);
        orderRepository.saveAll(completedOrders);
        orderRepository.flush();
    }

    private void processCompletedOrders(List<Order> completedOrders, LocalDateTime now) {
        for (Order order : completedOrders) {
            if (order.getDeliveryDate() == null) {
                log.warn("Delivery date is null for order ID: {}. Skipping return processing.", order.getId());
                continue;
            }
            if (now.isAfter(order.getDeliveryDate().plusMinutes(5))) {
                order.setStatus(OrderStatus.RETURN_NOT_ALLOWED);
                log.info("Order ID: {} updated to RETURN_NOT_ALLOWED.", order.getId());
            }
        }
    }

    private void processReturnRequestedOrders(List<Order> returnRequestedOrders, LocalDateTime now) {
        for (Order order : returnRequestedOrders) {
            if (order.getDeliveryDate() == null) {
                log.warn("Delivery date is null for order ID: {}. Skipping return processing.", order.getId());
                continue;
            }
            for (OrderItem item : order.getOrderItems()) {
                if (item.getStatus() == OrderItemStatus.RETURN_REQUESTED &&
                        now.isAfter(order.getDeliveryDate().plusMinutes(1))) {
                    item.setStatus(OrderItemStatus.RETURNED);
                    productClient.updateStock(item.getProductId(),
                            new StockUpdateRequestDto(item.getQuantity()));
                    log.info("Stock updated for Product ID: {} due to return of Order ID: {}", item.getProductId(), order.getId());
                }
            }
        }
    }
}