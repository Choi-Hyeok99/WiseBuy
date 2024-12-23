package com.sparta.haengye_project.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class OrderRequestDto {

    @NotEmpty(message = "주문 항목 리스트는 비어 있을 수 없습니다.")
    private List<OrderItemRequestDto> items;

    @NotBlank(message = "배송 주소는 필수 입력 값입니다.")
    private String shippingAddress; // 배송주소
}
