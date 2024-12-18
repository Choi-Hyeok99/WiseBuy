package com.sparta.haengye_project.entity;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@Builder
public class User {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long user_Id;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    private String name;

    private String phoneNumber;

    private String address;

    public User(Long user_Id, String email, String password, String name, String phoneNumber, String address) {
        this.user_Id = user_Id;
        this.email = email;
        this.password = password;
        this.name = name;
        this.phoneNumber = phoneNumber;
        this.address = address;
    }
}
