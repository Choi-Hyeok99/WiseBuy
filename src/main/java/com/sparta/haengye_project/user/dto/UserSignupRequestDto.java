package com.sparta.haengye_project.user.dto;

import lombok.Data;

@Data
public class UserSignupRequestDto {

    private String email;       // 이메일
    private String password;    // 비밀번호
    private String name;        // 이름
    private String phoneNumber; // 전화번호
    private String address;     // 주소

}
