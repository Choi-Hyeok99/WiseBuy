package com.sparta.haengye_project.user.entity;

import com.sparta.haengye_project.product.entitiy.Product;
import com.sparta.haengye_project.user.dto.UserResponseDto;
import com.sparta.haengye_project.wishlist.entity.Wishlist;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Entity
@Data
@NoArgsConstructor  // 기본 생성자 추가
public class User {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Enumerated(EnumType.STRING) // Enum 저장 방식 : 문자열
    private UserRole userRole;

    // **1:N 관계 매핑**
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Product> products = new ArrayList<>();

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Wishlist wishlist;


    public User(String email, String password, String name, String phoneNumber, String address, UserRole userRole) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.address = address;
        this.userRole = userRole;
        this.createdAt = LocalDateTime.now(); // 자동 설정
    }
    // User -> UserResponseDto 변환 메서드
    public UserResponseDto toResponseDto() {
        return new UserResponseDto(
                this.email,
                this.name,
                this.phoneNumber,
                this.address
        );
    }
}
