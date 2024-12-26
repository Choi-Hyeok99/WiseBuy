package com.sparta.haengye_project.user.service;

import com.sparta.haengye_project.user.repository.RedisRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private RedisRepository redisRepository;

    @InjectMocks
    private EmailService emailService;

    // 인증 코드 생성 단위 테스트
    @Test
    void createCode(){
        // When
        String code = emailService.createCode();

        // Then
        assertNotNull(code);
        assertEquals(6,code.length());
    }

    @Test
    void sendEmail() throws MessagingException{
        // Given : 테스트 준비
        // 테스트용 이메일 주소와 인증 코드 정의

        String email = "test@naver.com";
        String authCode = "123456";

        // JavaMailSender가 생성하는 MimeMessage 객체를 Mock으로 설정합니다.
        // JavaMailSender는 외부 의존성이므로 실제 동작 대신 Mock 객체로 대체합니다.
        MimeMessage mockMimeMessage = Mockito.mock(MimeMessage.class);

        // JavaMailSender의 createMimeMessage() 호출 시 Mock 객체를 반환하도록 설정합니다.
        when(mailSender.createMimeMessage()).thenReturn(mockMimeMessage);

        // When: 테스트 실행
        // EmailService의 sendEmail() 메서드를 호출하여 이메일 전송 로직을 실행합니다.
        emailService.sendEmail(email,authCode);

        // Then : 결과 검증
        // (1) MimeMessage 검증
        // JavaMailSender의 send() 메서드 호출 시 전달된 MimeMessage 객체를 캡처
        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(captor.capture());
        MimeMessage capturedMessage = captor.getValue();

        // 캡처된 MimeMessage 객체가 null이 아님을 확인합니다.
        // 이 검증을 통해 이메일 전송 로직이 정상적으로 실행되었는지 확인할 수 있습니다.
        assertNotNull(capturedMessage); // 객체가 null이 아닌지 검증


        // Redis 저장 검증
        // RedisRepository의 setDataExpire() 메서드가 정확히 한 번 호출되었는지 검증합니다.
        // 또한, 호출 시 전달된 이메일, 인증 코드, 만료 시간이 예상 값과 일치하는지 확인합니다.
        verify(redisRepository, times(1)).setDataExpire(email, authCode, 300);
    }
    @Test
    void verifyEmailCode_shouldReturnTrueAndDeleteCode() {
        // Given
        String email = "test@example.com";
        String authCode = "123456";

        // Mock 동작 설정: Redis에 데이터 저장 및 가져오기 동작
        when(redisRepository.getData(email)).thenReturn(authCode);

        // When
        boolean result = emailService.verifyEmailCode(email, authCode);

        // Then
        assertTrue(result, "verifyEmailCode 메서드는 true를 반환해야 합니다.");

        // 데이터 삭제 확인 (Mock에서는 deleteData가 호출되었는지 검증)
        verify(redisRepository, times(1)).deleteData(email);
    }
}