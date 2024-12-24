package com.sparta.haengye_project.order.dto;

import com.sparta.haengye_project.order.entity.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponseDto {

    private Long productId; // 상품 번호
    private String productName; // 상품 이름
    private int quantity; // 상품 수량
    private int price; // 상품 가격
    private String status; // 주문 항목 상태

    // OrderItem 객체를 기반으로 초기화하는 생성자
    public OrderItemResponseDto(OrderItem orderItem) {
        this.productId = orderItem.getProduct().getId();
        this.productName = orderItem.getProduct().getProductName();
        this.quantity = orderItem.getQuantity();
        this.price = orderItem.getPrice();
    }

}
