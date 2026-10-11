package com.crossborderpayment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.crossborderpayment.config.AuthProperties;

class OneTimeCodeServiceTests {

    private static AuthProperties properties(String hmacKey) {
        return new AuthProperties(List.of("http://localhost:3000"),
                new AuthProperties.WebAuthn("localhost", "Test"),
                new AuthProperties.Session(Duration.ofHours(8), Duration.ofMinutes(10), Duration.ofMinutes(5)),
                new AuthProperties.EmailVerification(Duration.ofMinutes(10), 5, 3, Duration.ofHours(1), hmacKey),
                new AuthProperties.Mail("no-reply@localhost"), true);
    }

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void codesHaveEightDigitsAndVary() {
        OneTimeCodeService service = new OneTimeCodeService(properties(KEY));
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            String code = service.generateCode();
            assertThat(code).matches("^[0-9]{8}$");
            codes.add(code);
        }
        assertThat(codes.size()).isGreaterThan(40);
    }

    @Test
    void hashIsBoundToPurposeAndAddress() {
        OneTimeCodeService service = new OneTimeCodeService(properties(KEY));
        String hash = service.hash("REGISTRATION", "a@example.com", "12345678");

        assertThat(service.matches(hash, "REGISTRATION", "a@example.com", "12345678")).isTrue();
        assertThat(service.matches(hash, "REGISTRATION", "a@example.com", "12345679")).isFalse();
        assertThat(service.matches(hash, "RECOVERY", "a@example.com", "12345678")).isFalse();
        assertThat(service.matches(hash, "REGISTRATION", "b@example.com", "12345678")).isFalse();
    }

    @Test
    void hashDependsOnTheKey() {
        String otherKey = Base64.getEncoder().encodeToString(new byte[] {
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27,
                28, 29, 30, 31, 32 });
        String a = new OneTimeCodeService(properties(KEY)).hash("RECOVERY", "a@example.com", "12345678");
        String b = new OneTimeCodeService(properties(otherKey)).hash("RECOVERY", "a@example.com", "12345678");

        assertThat(a).isNotEqualTo(b).hasSize(64);
    }

    @Test
    void randomKeyIsUsedWhenNoneIsConfigured() {
        OneTimeCodeService service = new OneTimeCodeService(properties(""));
        String hash = service.hash("RECOVERY", "a@example.com", "12345678");

        assertThat(service.matches(hash, "RECOVERY", "a@example.com", "12345678")).isTrue();
    }

    @Test
    void shortOrInvalidKeyIsRejectedAtStartup() {
        assertThatThrownBy(() -> new OneTimeCodeService(properties(Base64.getEncoder().encodeToString(new byte[16]))))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new OneTimeCodeService(properties("not base64 !!")))
                .isInstanceOf(IllegalStateException.class);
    }
}
