package com.sparta.haengye_project.security;

import com.sparta.haengye_project.user.jwt.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
public class JwtAuthorizationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthorizationFilter(JwtUtil jwtUtil, UserDetailsServiceImpl userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 요청 헤더에서 토큰 추출
        String token = getTokenFromRequest(request);

        // 로그 추가
        log.info("Authorization Header: {}", request.getHeader("Authorization"));
        log.info("Extracted Token: {}", token);

        if (token != null && jwtUtil.validateToken(token)) {
            // 토큰에서 이메일 추출
            String email = jwtUtil.getUserEmailFromToken(token);
            log.info("Authenticated email from token: {}", email);

            // UserDetailsService로 유저 정보 로드
            UserDetailsImpl userDetails = (UserDetailsImpl) userDetailsService.loadUserByUsername(email);
            log.info("UserDetails loaded: {}", userDetails.getUsername());

            // SecurityContextHolder에 인증 정보 설정
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        // 필터 체인 계속 진행
        filterChain.doFilter(request, response);
    }

    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}