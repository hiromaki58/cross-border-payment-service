package com.crossborderpayment.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;

import com.crossborderpayment.config.AuthProperties;

/**
 * Without an SMTP server no code could be delivered, so startup fails unless explicitly allowed (tests only).
 */
class AuthMailSenderTests {

    private static AuthProperties properties(boolean allowMissingSmtp) {
        return new AuthProperties(List.of("http://localhost:3000"),
                new AuthProperties.WebAuthn("localhost", "Test"),
                new AuthProperties.Session(Duration.ofHours(8), Duration.ofMinutes(10), Duration.ofMinutes(5)),
                new AuthProperties.EmailVerification(Duration.ofMinutes(10), 5, 3, Duration.ofHours(1), "", true),
                new AuthProperties.Mail("no-reply@example.com", allowMissingSmtp), true);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    private static MockEnvironment environment(String host) {
        MockEnvironment environment = new MockEnvironment();
        if (host != null) {
            environment.setProperty("spring.mail.host", host);
        }
        return environment;
    }

    @Test
    void startupFailsWhenNoSmtpHostIsConfigured() {
        assertThatThrownBy(() -> new AuthMailSender(provider(null), properties(false), environment(null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SPRING_MAIL_HOST");
    }

    @Test
    void startupFailsWhenTheSmtpHostIsBlank() {
        JavaMailSender sender = mock(JavaMailSender.class);

        assertThatThrownBy(() -> new AuthMailSender(provider(sender), properties(false), environment("  ")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void startupSucceedsAndMailIsSentWhenSmtpIsConfigured() {
        JavaMailSender sender = mock(JavaMailSender.class);
        AuthMailSender mailSender = new AuthMailSender(provider(sender), properties(false), environment("smtp.example.com"));

        mailSender.sendPasskeyAddedNotice("taro@example.com");

        verify(sender).send(any(SimpleMailMessage.class));
    }

    @Test
    void missingSmtpIsAcceptedOnlyWhenExplicitlyAllowed() {
        assertThatCode(() -> new AuthMailSender(provider(null), properties(true), environment(null)))
                .doesNotThrowAnyException();

        // Nothing is sent (and nothing fails) when allowed without an SMTP server
        AuthMailSender mailSender = new AuthMailSender(provider(null), properties(true), environment(null));
        assertThatCode(() -> mailSender.sendPasskeyAddedNotice("taro@example.com")).doesNotThrowAnyException();
    }
}
