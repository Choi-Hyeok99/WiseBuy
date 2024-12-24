package com.sparta.haengye_project.order.dto;

import com.sparta.haengye_project.order.entity.Order;
import com.sparta.haengye_project.order.entity.OrderStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor

public class OrderResponseDto {

    private Long orderId; // 주문 번호
    private LocalDateTime orderDate; // 주문 생성일
    private String shippingAddress; // 배송 주소
    private String status; // 주문 상태
    private int totalAmount; // 총 금액
    private LocalDateTime deliveryDate;
    private LocalDateTime cancelDate;
    private List<OrderItemResponseDto> items; // 주문 항목 리스트



    // Order 객체를 기반으로 초기화하는 생성자
    public OrderResponseDto(Order order) {
        this.orderId = order.getId();
        this.orderDate = order.getOrderDate();
        this.deliveryDate = order.getDeliveryDate();
        this.shippingAddress = order.getShippingAddress();
        this.cancelDate = order.getCancelDate();
        this.totalAmount = order.getTotalAmount();
        this.status = order.getStatus().name();
        this.items = order.getOrderItems().stream()
                          .map(OrderItemResponseDto::new)
                          .collect(Collectors.toList());
    }

    // 새로운 생성자 (조회 시 사용)
    public OrderResponseDto(Long id, LocalDateTime orderDate, String shippingAddress, OrderStatus status, int totalAmount, LocalDateTime deliveryDate, LocalDateTime cancelDate, List<OrderItemResponseDto> items) {
        this.orderId = id;
        this.orderDate = orderDate;
        this.shippingAddress = shippingAddress;
        this.status = status.name();
        this.totalAmount = totalAmount;
        this.deliveryDate = deliveryDate;
        this.cancelDate = cancelDate;
        this.items = items;
    }
}
