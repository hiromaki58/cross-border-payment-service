package com.crossborderpayment.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * The sender address has no default, so a missing or malformed APP_MAIL_FROM fails at startup.
 */
class AuthPropertiesTests {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = { "   ", "not-an-address", "no-reply@" })
    void missingOrMalformedSenderIsRejected(String from) {
        assertThat(validator.validate(new AuthProperties.Mail(from, false))).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = { "no-reply@example.com", "no-reply@localhost" })
    void validSenderIsAccepted(String from) {
        assertThat(validator.validate(new AuthProperties.Mail(from, false))).isEmpty();
    }
}
