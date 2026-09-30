package com.programmers.kdt.user.application.email;

import com.programmers.kdt.user.infrastructure.email.EmailSender;
import com.programmers.kdt.user.infrastructure.email.EmailVerificationCodeStore;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.user.domain.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 인터페이스 없이 클래스 하나로 (팀 컨벤션).
 */
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationCodeStore codeStore;
    private final EmailSender emailSender;

    public void sendVerificationCode(String email) {
        String code = codeStore.issueCode(email);
        emailSender.sendVerificationCode(email, code);
    }

    public void confirmVerificationCode(String email, String code) {
        boolean verified = codeStore.verifyAndMarkVerified(email, code);
        if (!verified) {
            throw new BusinessException(UserErrorCode.INVALID_VERIFICATION_CODE);
        }
    }

    /**
     * 회원가입 직전 UserService가 호출한다. 인증 마커를 1회성으로 소비하므로
     * 같은 인증으로 두 번 가입할 수 없다.
     */
    public void requireVerifiedEmail(String email) {
        if (!codeStore.isVerifiedAndConsume(email)) {
            throw new BusinessException(UserErrorCode.EMAIL_NOT_VERIFIED);
        }
    }
}
