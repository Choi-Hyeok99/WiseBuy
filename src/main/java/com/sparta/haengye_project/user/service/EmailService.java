package com.sparta.haengye_project.user.service;

import com.sparta.haengye_project.user.repository.RedisRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final RedisRepository redisRepository;


    public EmailService(JavaMailSender mailSender, RedisRepository redisRepository) {
        this.mailSender = mailSender;
        this.redisRepository = redisRepository;
    }
    public String createCode() {
        // 인증 코드 생성 로직
        return UUID.randomUUID().toString().substring(0, 6); // 예시로 랜덤 코드 생성
    }

    public void sendEmail(String to, String authCode) throws MessagingException {
        // MimeMessage 객체 생성
        MimeMessage message = mailSender.createMimeMessage();

        // MimeMessageHelper로 이메일 내용 설정
        MimeMessageHelper helper = new MimeMessageHelper(message, true);  // true는 멀티파트 메시지를 사용하겠다는 뜻

        helper.setTo(to);
        helper.setSubject("회원가입 인증 코드");
        helper.setFrom("gur0709@naver.com"); // 발송자 이메일 설정

        // HTML 형식으로 인증 코드 내용 작성
        String htmlContent = "<html><body>"
                + "<h1>회원가입 인증 코드</h1>"
                + "<p>아래 인증 코드를 사이트에 입력해주세요.</p>"
                + "<h2 style='color: crimson;'>" + authCode + "</h2>"
                + "</body></html>";

        helper.setText(htmlContent, true); // 두 번째 인자를 true로 설정하여 HTML 형식으로 전송

        mailSender.send(message); // 이메일 발송

        redisRepository.setDataExpire(to, authCode, 300);  // 300초 (5분) 동안 유효

    }
    public boolean verifyEmailCode(String email, String verifyCode) {
        String storedCode = redisRepository.getData(email);  // Redis에서 인증 코드 가져오기
        if (storedCode != null && storedCode.equals(verifyCode)) {
            redisRepository.deleteData(email);  // 인증 성공 후 Redis에서 코드 삭제
            return true;
        }
        return false;
    }
}
