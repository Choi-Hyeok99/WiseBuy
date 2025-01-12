package com.sparta.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class UserRequestDto {

    @Email
    @NotBlank(message = "이메일은 필수입니다.")
    private String email;

    @NotBlank(message = "이름은 필수입니다.")
    private String name;

    @NotBlank(message = "비밀번호는 필수입니다.")
    private String password;

    @NotBlank(message = "전화번호는 필수입니다.")
    private String phoneNumber;;

    @NotBlank(message = "주소는 필수입니다.")
    private String address;

    @NotBlank(message = "인증 코드는 필수입니다.")
    private String verificationToken; // verificationToken 필드 추가

    public UserRequestDto(String email, String password, String name, String phoneNumber, String address) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.address = address;
    }
}
