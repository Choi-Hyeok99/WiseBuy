package com.sparta.haengye_project.user.controller;

import com.sparta.haengye_project.jwt.JwtUtil;
import com.sparta.haengye_project.user.dto.LoginRequestDto;
import com.sparta.haengye_project.user.dto.LoginResponseDto;
import com.sparta.haengye_project.user.dto.UserRequestDto;
import com.sparta.haengye_project.user.dto.UserResponseDto;
import com.sparta.haengye_project.user.service.UserService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/user")
public class UserController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager; // AuthenticationManager
    private final JwtUtil jwtUtil;  // JwtUtil


    // 이메일 인증 토큰 발송
    @PostMapping("/send-email")
    public ResponseEntity<?> sendEmail(@RequestParam String email) throws MessagingException {
        userService.sendEmailVerificationToken(email);
        return ResponseEntity.ok("인증 토큰이 발송되었습니다.");
    }

    @PostMapping("/signup")
    public ResponseEntity<UserResponseDto> signup(@RequestBody UserRequestDto requestDto) {
        try {
            UserResponseDto responseDto = userService.signup(requestDto, requestDto.getVerificationToken());
            return ResponseEntity.ok(responseDto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null); // 인증 코드 오류나 중복 이메일 발생 시 처리
        } catch (MessagingException e) {
            return ResponseEntity.status(500)
                                 .body(null);  // 이메일 인증 처리 중 오류 발생 시
        }
    }

    // 로그인 처리
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDto loginRequestDto) {
        log.info("Login Request: email={}, password={}", loginRequestDto.getEmail(), loginRequestDto.getPassword());
        try {
            // 사용자 인증
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(loginRequestDto.getEmail(), loginRequestDto.getPassword());
            log.info("Generated AuthenticationToken for email: {}", loginRequestDto.getEmail());

            Authentication authentication = authenticationManager.authenticate(authenticationToken);
            log.info("Authentication successful for email: {}", loginRequestDto.getEmail());


            // 인증 성공 후 SecurityContext에 인증 정보를 저장
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // JWT 토큰 생성
            String token = jwtUtil.generateToken(loginRequestDto.getEmail());
            log.info("Generated JWT token for email: {}", loginRequestDto.getEmail());

            // JWT 토큰을 응답으로 반환
            return ResponseEntity.ok(new LoginResponseDto(token));
        } catch (Exception e) {
            log.error("Login failed for email: {}", loginRequestDto.getEmail(), e);
            return ResponseEntity.status(401).body("로그인 실패");  // 인증 실패 시
        }
    }
}
