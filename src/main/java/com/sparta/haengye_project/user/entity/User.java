package com.sparta.haengye_project.user.entity;

import com.sparta.haengye_project.user.config.security.crypto.EncryptConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Entity
@Data
@NoArgsConstructor  // 기본 생성자 추가
@AllArgsConstructor
public class User {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(unique = true, nullable = false)
    @Convert(converter = EncryptConverter.class) // 암호화된 이메일
    private String email;

    private String password;

    private String name;

    private String phoneNumber;

    @Convert(converter = EncryptConverter.class) // 암호화된 주소
    private String address;

}
