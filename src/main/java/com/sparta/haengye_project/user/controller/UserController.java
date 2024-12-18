package com.sparta.haengye_project.user.controller;


import com.sparta.haengye_project.user.dto.UserSignupRequestDto;
import com.sparta.haengye_project.user.dto.UserSignupResponseDto;
import com.sparta.haengye_project.user.service.UserService;
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
