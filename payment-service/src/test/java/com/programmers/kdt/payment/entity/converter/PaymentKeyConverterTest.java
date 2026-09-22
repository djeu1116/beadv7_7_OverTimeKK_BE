package com.programmers.kdt.payment.entity.converter;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.payment.exception.PaymentErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class PaymentKeyConverterTest {

    private PaymentKeyConverter converter;

    @BeforeEach
    void setUp() throws Exception {
        converter = new PaymentKeyConverter();
        ReflectionTestUtils.setField(converter, "secret", generateBase64Key());
    }

    @Test
    @DisplayName("암호화한 값을 복호화하면 원래 평문과 같다.")
    void encryptThenDecrypt_returnsOriginalValue() {
        String plaintText = "tviva20260722175632w3kG4";

        String encrypted = converter.convertToDatabaseColumn(plaintText);
        String decrypted = converter.convertToEntityAttribute(encrypted);

        assertThat(encrypted).isNotNull().isNotEqualTo(plaintText);
        assertThat(decrypted).isEqualTo(plaintText);
    }

    private String generateBase64Key() throws Exception {
        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);
        SecretKey secretKey = keyGenerator.generateKey();
        return Base64.getEncoder().encodeToString(secretKey.getEncoded());
    }

    @Test
    @DisplayName("같은 평문이어도 암호화할 때마다 다른 암호문이 나온다.")
    void sameSecretEncryptedTwice_producesDifferentCipherText() {
        String plaintText = "tviva20260722175632w3kG4";

        String encrypted1 = converter.convertToDatabaseColumn(plaintText);
        String encrypted2 = converter.convertToDatabaseColumn(plaintText);

        assertThat(encrypted1).isNotEqualTo(encrypted2);
    }

    @Test
    @DisplayName("null을 암호화/복호화하면 null을 반환한다.")
    void nullInput_returnsNull() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull();
        assertThat(converter.convertToEntityAttribute(null)).isNull();
    }

    @Test
    @DisplayName("암호문이 변조되면 복호화 시 예외가 발생한다.")
    void tamperedCipherText_throwsDecryptionFailedException() {
        String encrypted = converter.convertToDatabaseColumn("tviva20260722175632w3kG4");

        int mid = encrypted.length() / 2;
        char original = encrypted.charAt(mid);
        char replacement = original == 'A' ? 'B' : 'A';
        String tampered = encrypted.substring(0, mid) + replacement + encrypted.substring(mid + 1);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.DECRYPTION_FAILED);
    }

    @Test
    @DisplayName("다른 키로 복호화를 시도하면 예외가 발생한다.")
    void decryptWithWrongKey_throwsDecryptionFailedException() throws Exception {
        String encrypted = converter.convertToDatabaseColumn("tviva20260722175632w3kG4");

        PaymentKeyConverter otherConverter = new PaymentKeyConverter();
        ReflectionTestUtils.setField(otherConverter, "secret", generateBase64Key());

        assertThatThrownBy(() -> otherConverter.convertToEntityAttribute(encrypted))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.DECRYPTION_FAILED);
    }

    @Test
    @DisplayName("암호화된 문자열의 첫 바이트는 버전이다.")
    void encryptedString_startsWithVersionByte() {
        String plaintText = "tviva20260722175632w3kG4";

        String encrypted = converter.convertToDatabaseColumn(plaintText);
        byte[] decoded = Base64.getDecoder().decode(encrypted);

        assertThat(decoded[0]).isEqualTo((byte) 1);
    }

    @Test
    @DisplayName("암호화된 문자열의 버전이 다르면 복호화 시 예외가 발생한다.")
    void decryptWithWrongVersion_throwsDecryptionFailedException() throws Exception {
        String encrypted = converter.convertToDatabaseColumn("tviva20260722175632w3kG4");

        byte[] decoded = Base64.getDecoder().decode(encrypted);
        decoded[0] = 3;
        String tampered = Base64.getEncoder().encodeToString(decoded);

        assertThatThrownBy(() -> converter.convertToEntityAttribute(tampered))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(PaymentErrorCode.DECRYPTION_FAILED);
    }



}