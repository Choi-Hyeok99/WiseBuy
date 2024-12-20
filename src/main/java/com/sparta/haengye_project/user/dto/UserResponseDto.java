package com.sparta.haengye_project.user.dto;

import lombok.Data;

@Data
public class UserResponseDto {

    private String email;
    private String name;
    private String phoneNumber;
    private String address;

    public UserResponseDto(String email, String name, String phoneNumber, String address) {
        this.email = email;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.address = address;
    }
}
