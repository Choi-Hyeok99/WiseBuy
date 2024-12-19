package com.sparta.haengye_project.user.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service

public class EmailService {


    private JavaMailSender javaMailSender;

    public EmailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    // 이메일 발송
    public void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        message.setFrom("EMAIL_USERNAME");  // 발신자 이메일 (환경변수 사용 가능)
        javaMailSender.send(message);
    }

    // 인증 이메일 보내기
    public void sendVerificationEmail(String to, String verificationLink) {
        String subject = "이메일 인증";
        String text = "아래 링크를 클릭하여 이메일 인증을 완료해주세요.\n" + verificationLink;
        sendEmail(to, subject, text);
    }

}
