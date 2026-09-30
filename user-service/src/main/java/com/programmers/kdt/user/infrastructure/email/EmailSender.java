package com.programmers.kdt.user.infrastructure.email;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.user.domain.exception.UserErrorCode;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailSender {

    private final JavaMailSender mailSender;

    public EmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationCode(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[ReSeat] 이메일 인증 코드");
        message.setText("인증 코드: " + code);
        mailSender.send(message);
    }
    public void send(String to, String subject, String text) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            mailSender.send(message);
        } catch (MailException e) {
            // 메일 발송 실패 시 추가해둔 500 에러 코드 발송하즈아아
            throw new BusinessException(UserErrorCode.MAIL_SEND_FAILED);
        }
    }

}
