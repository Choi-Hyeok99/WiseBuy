package com.sparta.haengye_project.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class OrderItemRequestDto {

    @NotNull(message = "상품 아이디는 필수 입력 값입니다.")
    private Long productId; // 상품 ID

    @Min(value = 1 , message = "수량은 최소 1개 이상이어야 합니다.")
    private int quantity; // 수량

}
