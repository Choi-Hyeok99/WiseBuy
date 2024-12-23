package com.sparta.haengye_project.wishlist.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WishlistUpdateRequestDto {

    @NotNull(message = "수량은 필수 입력 값입니다.")
    @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
    private int quantity;

}
