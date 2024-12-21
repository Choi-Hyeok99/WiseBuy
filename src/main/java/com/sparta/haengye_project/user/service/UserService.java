package com.sparta.haengye_project.user.service;

import com.sparta.haengye_project.user.dto.UserRequestDto;
import com.sparta.haengye_project.user.dto.UserResponseDto;
import com.sparta.haengye_project.user.entity.User;
import com.sparta.haengye_project.user.entity.UserRole;
import com.sparta.haengye_project.user.exception.EmailAlreadyExistsException;
import com.sparta.haengye_project.user.exception.InvalidEmailCodeException;
import com.sparta.haengye_project.user.repository.UserRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncryptionService passwordEncryptionService; // 암호화 서비스 추가

    // 회원가입 처리 (이메일 인증 포함)
    public UserResponseDto signup(UserRequestDto requestDto, String verificationCode) throws MessagingException {
        // 이메일 인증 코드 확인
        if (!emailService.verifyEmailCode(requestDto.getEmail(), verificationCode)) {
            throw new InvalidEmailCodeException("잘못된 인증 코드입니다.");
        }

        // 이메일 중복 확인
        if (userRepository.findByEmail(requestDto.getEmail()).isPresent()) {
            throw new EmailAlreadyExistsException("이미 존재하는 이메일입니다.");
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncryptionService.encodePassword(requestDto.getPassword());

        // 회원가입 처리
        User user = new User();
        user.setEmail(requestDto.getEmail());
        user.setPassword(encodedPassword);
        user.setName(requestDto.getName());
        user.setPhoneNumber(requestDto.getPhoneNumber());
        user.setAddress(requestDto.getAddress());

        // **기본 역할 설정 추가**
        user.setUserRole(UserRole.USER); // 일반 사용자 역할로 설정

        userRepository.save(user);

        // ResponseDto로 반환
        return new UserResponseDto(
                user.getEmail(),
                user.getName(),
                user.getPhoneNumber(),
                user.getAddress()
        );
    }
    public void sendEmailVerificationToken(String email) throws MessagingException {
        String authCode = emailService.createCode();  // 인증 코드 생성
        emailService.sendEmail(email, authCode);  // 이메일 발송
    }
}
