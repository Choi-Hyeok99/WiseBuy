package com.sparta.haengye_project.user.controller;

import com.sparta.haengye_project.user.dto.LoginRequestDto;
import com.sparta.haengye_project.user.dto.LoginResponseDto;
import com.sparta.haengye_project.user.service.LoginService;
import com.sparta.haengye_project.user.service.TokenService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/user")
public class LoginController {


    private final LoginService loginService;
    private final TokenService tokenService;


    @PostMapping("/login")
    public LoginResponseDto login(@RequestBody LoginRequestDto loginRequestDto){

        // 로그인 서비스 호출하여 JWT 토큰 생성
      String token =   loginService.login(loginRequestDto.getEmail(), loginRequestDto.getPassword());


        return new LoginResponseDto(token);
    }
    // 로그아웃
    @PostMapping("/logout")
    public String logout(@RequestHeader("Authorization") String token){

        // Bearer 접두사 제외 토큰만 가져오기
        String jwToken = token.substring(7);

        // 블랙리스트에 토큰 추가
        tokenService.addToBlacklist(jwToken);

        return "로그아웃 되었습니다.";
    }
}
