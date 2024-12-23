package com.sparta.haengye_project.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponseDto {

    private Long orderId; // 주문 번호
    private LocalDateTime orderDate; // 주문 생성일
    private String shippingAddress; // 배송 주소
    private String status; // 주문 상태
    private int totalAmount; // 총 금액
    private List<OrderItemResponseDto> items; // 주문 항목 리스트


}
