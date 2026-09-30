package com.programmers.kdt.user.infrastructure.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * 이메일 인증코드를 Redis에 TTL로 저장/검증한다 (RefreshTokenStore와 동일한 저장 패턴).
 * 코드 검증에 성공하면 code 키는 지우고 verified 마커를 별도 키로 남겨,
 * 회원가입 시점에 "이 이메일이 방금 인증됐는지"를 1회성으로 소비할 수 있게 한다.
 */
@Component
public class EmailVerificationCodeStore {

    private static final String CODE_KEY_PREFIX = "email-verify-code:";
    private static final String VERIFIED_KEY_PREFIX = "email-verified:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final Duration codeTtl;
    private final Duration verifiedTtl;

    public EmailVerificationCodeStore(StringRedisTemplate redisTemplate,
                                       @Value("${email.verification.code-ttl-seconds}") long codeTtlSeconds,
                                       @Value("${email.verification.verified-ttl-seconds}") long verifiedTtlSeconds) {
        this.redisTemplate = redisTemplate;
        this.codeTtl = Duration.ofSeconds(codeTtlSeconds);
        this.verifiedTtl = Duration.ofSeconds(verifiedTtlSeconds);
    }

    public String issueCode(String email) {
        String code = generateCode();
        redisTemplate.opsForValue().set(codeKey(email), code, codeTtl);
        return code;
    }

    public boolean verifyAndMarkVerified(String email, String code) {
        String savedCode = redisTemplate.opsForValue().get(codeKey(email));
        if (savedCode == null || !savedCode.equals(code)) {
            return false;
        }
        redisTemplate.delete(codeKey(email));
        redisTemplate.opsForValue().set(verifiedKey(email), "true", verifiedTtl);
        return true;
    }

    public boolean isVerifiedAndConsume(String email) {
        Boolean deleted = redisTemplate.delete(verifiedKey(email));
        return Boolean.TRUE.equals(deleted);
    }

    private String generateCode() {
        int number = SECURE_RANDOM.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    private String codeKey(String email) {
        return CODE_KEY_PREFIX + email;
    }

    private String verifiedKey(String email) {
        return VERIFIED_KEY_PREFIX + email;
    }
}
