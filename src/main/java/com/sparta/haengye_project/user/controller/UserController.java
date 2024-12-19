package com.sparta.haengye_project.user.controller;


import com.sparta.haengye_project.user.dto.ChangePasswordRequestDto;
import com.sparta.haengye_project.user.dto.UserSignupRequestDto;
import com.sparta.haengye_project.user.dto.UserSignupResponseDto;
import com.sparta.haengye_project.user.service.TokenService;
import com.sparta.haengye_project.user.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@AllArgsConstructor
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final TokenService tokenService;

    // 회원가입 API
    @PostMapping("/signup")
    public UserSignupResponseDto signup(@RequestBody UserSignupRequestDto requestDto){
        return userService.signup(requestDto);
    }
    // 이메일 인증 API
    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam String token, @RequestParam String userEmail) {
        boolean isVerified = tokenService.verifyEmailToken(token,userEmail);  // 토큰 검증
        if (isVerified) {
            return "이메일 인증이 완료되었습니다!";
        } else {
            return "잘못된 인증 링크입니다.";
        }
    }
    // 비밀번호 변경 API
    @PutMapping("/{userId}/password")
    public String changePassword(@PathVariable Long userId, @RequestBody ChangePasswordRequestDto requestDto){
        userService.changePassword(userId,requestDto);
        return "비밀번호가 변경되었습니다.";
    }

}
