package com.sparta.user.dto;

public class EmailDto {
    private String email;        // 인증을 위한 이메일
    private String verifyCode;   // 인증 코드 (사용자 입력)

    // 생성자
    public EmailDto(String email, String verifyCode) {
        this.email = email;
        this.verifyCode = verifyCode;
    }

    // Getter & Setter
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getVerifyCode() {
        return verifyCode;
    }

    public void setVerifyCode(String verifyCode) {
        this.verifyCode = verifyCode;
    }
}
