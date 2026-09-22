package com.programmers.kdt.payment.entity.converter;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Slf4j
@Component
@Converter
public class PaymentKeyConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTE = 12; // GCM 권장 IV 길이(96bit)
    private static final int TAG_LENGTH_BIT = 128; // 인증 태그 길이
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final byte CURRENT_KEY_VERSION = 1;

    @Value("${encryption.payment-key-secret}")
    private String secret;

    @Override
    public String convertToDatabaseColumn(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(1 + iv.length + cipherText.length);
            buffer.put(CURRENT_KEY_VERSION);
            buffer.put(iv);
            buffer.put(cipherText);

            return Base64.getEncoder().encodeToString(buffer.array());

        } catch (Exception e) {
            log.error("payment 암호화 실패", e);
            throw new BusinessException(PaymentErrorCode.ENCRYPTION_FAILED);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(dbData);

            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte version = buffer.get();
            if (version != CURRENT_KEY_VERSION) { // 키가 여러 개 생기면 switch문으로 버전별 처리 가능
                throw new BusinessException(PaymentErrorCode.DECRYPTION_FAILED);
            }
            byte[] iv = new byte[IV_LENGTH_BYTE];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), new GCMParameterSpec(TAG_LENGTH_BIT, iv));

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("paymentKey 복호화 실패", e);
            throw new BusinessException(PaymentErrorCode.DECRYPTION_FAILED);
        }
    }

    private SecretKey getSecretKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secret);
        return new SecretKeySpec(keyBytes, "AES");
    }
}
