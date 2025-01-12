package com.sparta.order.dto;

import com.sparta.order.entity.OrderItem;
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
        this.productId = orderItem.getProductId(); // productId 직접 사용
        this.productName = productName; // 외부 서비스에서 조회한 productName
        this.quantity = orderItem.getQuantity();
        this.price = orderItem.getPrice();
        this.status = orderItem.getStatus().name(); // Enum 타입 문자열로 변환
    }
    // 정적 팩토리 메서드 추가
    public static OrderItemResponseDto from(OrderItem orderItem, String productName) {
        return new OrderItemResponseDto(
                orderItem.getProductId(),
                productName,
                orderItem.getQuantity(),
                orderItem.getPrice(),
                orderItem.getStatus().name()
        );
    }
}
