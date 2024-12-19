package com.sparta.haengye_project.user.service;


import com.sparta.haengye_project.user.dto.UserSignupRequestDto;
import com.sparta.haengye_project.user.dto.UserSignupResponseDto;
import com.sparta.haengye_project.user.entity.User;
import com.sparta.haengye_project.user.repository.UserRepository;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;


@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final TokenService tokenService;



    public UserSignupResponseDto signup(UserSignupRequestDto requestDto) {

        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());

        Optional<User> existEmail = userRepository.findByEmail(requestDto.getEmail());
        if (existEmail.isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        User user = new User();
        user.setEmail(requestDto.getEmail());
        user.setPassword(encodedPassword);
        user.setName(requestDto.getName());
        user.setPhoneNumber(requestDto.getPhoneNumber());
        user.setAddress(requestDto.getAddress());

        // User 저장
        User savedUser = userRepository.save(user);

        // 인증 토큰 생성 (랜덤 UUID 사용)
        String token = UUID.randomUUID().toString();

        // 레딧 토큰 저장 ( 24 시간 )
        tokenService.createEmailVerificationToken(requestDto.getEmail(), token);


        // 인증 이메일 발송
        String verificationLink = "http://haengye_prodject:8090/user/verify-email?token=" + token;
        emailService.sendVerificationEmail(requestDto.getEmail(), verificationLink);


        return new UserSignupResponseDto(savedUser.getUserId(), savedUser.getEmail(), savedUser.getName());

    }
}
