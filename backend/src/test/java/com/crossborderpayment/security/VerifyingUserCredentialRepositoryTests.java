package com.crossborderpayment.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class VerifyingUserCredentialRepositoryTests {

    @Test
    void counterMustIncreaseWhenUsed() {
        assertThatCode(() -> VerifyingUserCredentialRepository.verifySignatureCounter(5, 6)).doesNotThrowAnyException();
        assertThatThrownBy(() -> VerifyingUserCredentialRepository.verifySignatureCounter(5, 5))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> VerifyingUserCredentialRepository.verifySignatureCounter(5, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void authenticatorsWithoutCounterAreAccepted() {
        // Synced passkeys usually always report 0
        assertThatCode(() -> VerifyingUserCredentialRepository.verifySignatureCounter(0, 0)).doesNotThrowAnyException();
    }

    @Test
    void labelMustBeShortPrintableText() {
        assertThatCode(() -> VerifyingUserCredentialRepository.validateLabel("My phone")).doesNotThrowAnyException();
        assertThatCode(() -> VerifyingUserCredentialRepository.validateLabel("x".repeat(64))).doesNotThrowAnyException();
        assertThatThrownBy(() -> VerifyingUserCredentialRepository.validateLabel("x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VerifyingUserCredentialRepository.validateLabel(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VerifyingUserCredentialRepository.validateLabel(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> VerifyingUserCredentialRepository.validateLabel("line1\nline2"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
