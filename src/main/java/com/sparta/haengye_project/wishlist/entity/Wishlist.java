package com.sparta.haengye_project.wishlist.entity;

import com.sparta.haengye_project.user.entity.User;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "wishlist",cascade = CascadeType.ALL,orphanRemoval = true)
    private List<WishListItem> items = new ArrayList<>();

}
