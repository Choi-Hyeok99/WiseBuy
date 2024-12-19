package com.sparta.haengye_project.user.service;


import com.sparta.haengye_project.user.dto.ChangePasswordRequestDto;
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

        // 레딧 토큰 저장
        tokenService.createEmailVerificationToken(requestDto.getEmail(), token);


        // 인증 이메일 발송
        String verificationLink = "http://haengye_prodject:8090/user/verify-email?token=" + token;
        emailService.sendVerificationEmail(requestDto.getEmail(), verificationLink);


        return new UserSignupResponseDto(savedUser.getUserId(), savedUser.getEmail(), savedUser.getName());

    }

    // 비밀번호 변경
    public void changePassword(Long userId, ChangePasswordRequestDto requestDto) {

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()){
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }

        User user = userOptional.get();

        // 2. 현재 비밀번호 검증 ( 기존 비밀번호와 입력된 비밀번호 비교 )
        if (!passwordEncoder.matches(requestDto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 잘못되었습니다.");
        }

        // 새로운 비밀번호 암호화
        String encodedNewPassword = passwordEncoder.encode(requestDto.getNewPassword());

        // 4. 새로운 비밀번호로 업데이트
        user.setPassword(encodedNewPassword);

        // 5. 사용자 정보 업데이트
        userRepository.save(user);

    }
}
