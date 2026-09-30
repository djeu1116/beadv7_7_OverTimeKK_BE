package com.programmers.kdt.user.application.email;

import com.programmers.kdt.user.infrastructure.email.EmailSender;
import com.programmers.kdt.user.infrastructure.email.EmailVerificationCodeStore;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.user.domain.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationCodeStore codeStore;

    @Mock
    private EmailSender emailSender;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Test
    void 인증코드_발송시_코드를_발급해서_메일로_보낸다() {
        String email = "user1@example.com";
        given(codeStore.issueCode(email)).willReturn("123456");

        emailVerificationService.sendVerificationCode(email);

        verify(emailSender).sendVerificationCode(email, "123456");
    }

    @Test
    void 인증코드_확인_성공시_예외없이_통과() {
        String email = "user1@example.com";
        given(codeStore.verifyAndMarkVerified(email, "123456")).willReturn(true);

        assertThatCode(() -> emailVerificationService.confirmVerificationCode(email, "123456"))
                .doesNotThrowAnyException();
    }

    @Test
    void 인증코드_불일치시_예외() {
        String email = "user1@example.com";
        given(codeStore.verifyAndMarkVerified(email, "000000")).willReturn(false);

        assertThatThrownBy(() -> emailVerificationService.confirmVerificationCode(email, "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.INVALID_VERIFICATION_CODE.getMessage());
    }

    @Test
    void 인증완료된_이메일이면_가입게이트_통과() {
        String email = "user1@example.com";
        given(codeStore.isVerifiedAndConsume(email)).willReturn(true);

        assertThatCode(() -> emailVerificationService.requireVerifiedEmail(email))
                .doesNotThrowAnyException();
    }

    @Test
    void 인증되지_않은_이메일이면_가입게이트에서_예외() {
        String email = "user1@example.com";
        given(codeStore.isVerifiedAndConsume(email)).willReturn(false);

        assertThatThrownBy(() -> emailVerificationService.requireVerifiedEmail(email))
                .isInstanceOf(BusinessException.class)
                .hasMessage(UserErrorCode.EMAIL_NOT_VERIFIED.getMessage());
    }
}
