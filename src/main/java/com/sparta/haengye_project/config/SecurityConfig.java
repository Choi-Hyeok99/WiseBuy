package com.sparta.haengye_project.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // BCryptPasswordEncoder 빈 등록
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // HTTP 보안 설정
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // csrf 보호 비활성화 (Postman에서 테스트할 때 필요)
        http.csrf().disable()
            // 인증 없이 접근할 수 있는 URL 설정
            .authorizeRequests()
            .requestMatchers("/user/send-email", "/user/signup").permitAll()
            // 인증된 사용자만 접근할 수 있는 URL 설정
            .anyRequest().authenticated();

        return http.build();
    }
}
