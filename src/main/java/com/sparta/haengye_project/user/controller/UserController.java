package com.sparta.haengye_project.user.controller;

import com.sparta.haengye_project.user.dto.UserRequestDto;
import com.sparta.haengye_project.user.dto.UserResponseDto;
import com.sparta.haengye_project.user.service.UserService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    @GetMapping
    public String test(){
        return "hello zz";
    }


    // 이메일 인증 토큰 발송
    @PostMapping("/send-email")
    public ResponseEntity<?> sendEmail(@RequestParam String email) throws MessagingException {
        System.out.println("이메일 타는지 "+ email);
        userService.sendEmailVerificationToken(email);
        return ResponseEntity.ok("인증 토큰이 발송되었습니다.");
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponseDto> signup(@RequestBody UserRequestDto requestDto) {

        try {
            System.out.println("Request DTO: " + requestDto);
            UserResponseDto responseDto = userService.signup(requestDto, requestDto.getVerificationToken());
            return ResponseEntity.ok(responseDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null); // 인증 코드 오류나 중복 이메일 발생 시 처리
        } catch (MessagingException e) {
            return ResponseEntity.status(500)
                                 .body(null);  // 이메일 인증 처리 중 오류 발생 시
        }
    }

}
