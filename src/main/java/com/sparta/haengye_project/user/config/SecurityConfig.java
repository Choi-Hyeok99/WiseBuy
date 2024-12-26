package com.sparta.haengye_project.user.config;

import com.sparta.haengye_project.user.jwt.JwtUtil;
import com.sparta.haengye_project.security.JwtAuthorizationFilter;

import com.sparta.haengye_project.security.UserDetailsServiceImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService; // UserDetailsServiceImpl 추가
    private final AuthenticationManager authenticationManager;  // AuthenticationManager 추가


    public SecurityConfig(@Lazy JwtUtil jwtUtil, @Lazy UserDetailsServiceImpl userDetailsService, @Lazy AuthenticationManager authenticationManager) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
        this.authenticationManager = authenticationManager;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // AuthenticationManager 빈 등록
    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder authenticationManagerBuilder = http.getSharedObject(AuthenticationManagerBuilder.class);
        authenticationManagerBuilder.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
        return authenticationManagerBuilder.build();
    }


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .authorizeRequests()
            .requestMatchers("/user/send-email", "/user/signup","/user/login").permitAll()
            .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
            .requestMatchers(HttpMethod.GET, "/wishlist/**").authenticated() // GET 요청에 대한 인증 필요 추가
            .requestMatchers(HttpMethod.POST, "/wishlist/**").authenticated()
            .requestMatchers(HttpMethod.PUT, "/wishlist/**").authenticated()
            .requestMatchers(HttpMethod.DELETE, "/wishlist/**").authenticated()
            .requestMatchers(HttpMethod.POST,"/orders/**").authenticated()
            .requestMatchers(HttpMethod.GET,"/orders/**").authenticated()
            .requestMatchers(HttpMethod.DELETE,"/orders/**").authenticated()
            .requestMatchers(HttpMethod.PATCH,"/orders/**").authenticated()


            .anyRequest().authenticated()
            .and()
            .addFilterBefore(new JwtAuthorizationFilter(jwtUtil, userDetailsService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
