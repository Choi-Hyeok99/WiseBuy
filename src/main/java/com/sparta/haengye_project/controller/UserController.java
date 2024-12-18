package com.sparta.haengye_project.controller;

import com.sparta.haengye_project.dto.UserSignupResponseDto;
import com.sparta.haengye_project.dto.UserSignupRequestDto;
import com.sparta.haengye_project.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
@RequestMapping("/user/signup")
public class UserController {

    private final UserService userService;

    @PostMapping
    public UserSignupResponseDto signup(@RequestBody UserSignupRequestDto requestDto){

        return userService.signup(requestDto);

    }
}
