package com.crossborderpayment.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Authentication settings (app.security.*). Validated at startup so a missing value fails fast.
 */
@Validated
@ConfigurationProperties("app.security")
public record AuthProperties(
        @NotEmpty List<@NotBlank String> allowedOrigins,
        @Valid @NotNull WebAuthn webauthn,
        @Valid @NotNull Session session,
        @Valid @NotNull EmailVerification emailVerification,
        @Valid @NotNull Mail mail,
        boolean secureCookies) {

    public record WebAuthn(@NotBlank String rpId, @NotBlank String rpName) {
    }

    public record Session(
            @NotNull Duration absoluteTimeout,
            @NotNull Duration enrollmentTimeout,
            @NotNull Duration reauthenticationWindow) {
    }

    public record EmailVerification(
            @NotNull Duration codeTtl,
            @Min(1) int maxAttempts,
            @Min(1) int maxRequestsPerWindow,
            @NotNull Duration requestWindow,
            String hmacKey,
            // Local development and tests only: a random per-process key when hmacKey is empty
            boolean allowRandomHmacKey) {
    }

    public record Mail(@NotBlank @Email String from) {
    }
}
