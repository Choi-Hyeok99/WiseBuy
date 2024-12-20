package com.sparta.haengye_project.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequestDto {

    @Email(message = "유효한 이메일을 입력해주세요")
    @NotBlank
    private String email;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

}
