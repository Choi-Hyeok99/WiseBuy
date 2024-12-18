package com.sparta.haengye_project.repository;

import com.sparta.haengye_project.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User,Long> {
}
