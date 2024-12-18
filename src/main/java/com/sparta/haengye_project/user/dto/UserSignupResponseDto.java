package com.sparta.haengye_project.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserSignupResponseDto {
    private Long userId;
    private String email;
    private String name;

}
