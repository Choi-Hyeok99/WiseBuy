package com.sparta.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class ProductRequestDto {

    @NotBlank(message = "상품 이름은 필수 입력값입니다.")
    @Size(max = 255,message = "상품 이름은 255자 이하로 입력해주세요.")
    private String productName;

    @Min(value = 0, message = "재고는 0 이상이어야 합니다.")
    private Integer stock;

    @NotNull(message = "상품 가격은 필수 입력값입니다.")
    @Min(value = 0, message = "가격은 0 이상이어야 합니다.")
    private Integer price;

    @NotBlank(message = "상품 설명은 필수 입력값입니다.")
    private String description;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private String imagePath;


    @NotBlank(message = "상품 상태는 필수 입력값입니다.")
    private String status; // 추가 (AVAILABLE, UNAVAILABLE)

    @NotBlank(message = "상품 유형은 필수 입력값입니다.")
    private String productType; // 추가 (GENERAL, FLASH_SALE)


}
