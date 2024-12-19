package com.sparta.haengye_project.user.service;

import com.sparta.haengye_project.user.entity.User;
import com.sparta.haengye_project.user.repository.UserRepository;
import com.sparta.haengye_project.user.config.security.jwt.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LoginService {


    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TokenService tokenService;


    // 로그인
    public String login(String email, String password) {

        Optional<User> userEmail = userRepository.findByEmail(email);
        if (userEmail.isEmpty()) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 잘못되었습니다.");
        }

        User user = userEmail.get();

        // 비밀번호 검증
        if (!passwordEncoder.matches(password,user.getPassword())){
            throw new IllegalArgumentException("이메일 또는 비밀번호가 틀렸습니다.");
        }


        // JWT 토큰 생성
        return jwtTokenProvider.createToken(user.getEmail());
    }

    // 로그아웃
    public void logout(String token){
        // 토큰을 블랙리스트에 추가하여 로그아웃 처리
        tokenService.addToBlacklist(token);
    }
}
