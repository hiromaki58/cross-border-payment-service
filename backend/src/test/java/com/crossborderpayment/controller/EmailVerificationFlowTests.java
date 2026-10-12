package com.crossborderpayment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.crossborderpayment.support.AuthIntegrationTestSupport;

/**
 * Registration / recovery start and e-mail one-time code verification.
 */
class EmailVerificationFlowTests extends AuthIntegrationTestSupport {

    private static final String EMAIL = "taro@example.com";

    // --- registration ---

    @Test
    void registrationSendsCodeAndVerificationStartsEnrollmentSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        startRegistration(session, "Taro@Example.COM")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(AuthController.ACCEPTED_MESSAGE));
        String anonymousSessionId = session.getId();

        submitCode(session, lastCodeSentTo(EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("PASSKEY_ENROLLMENT"));

        // Session fixation protection: the session id changes when the user is authenticated
        assertThat(session.getId()).isNotEqualTo(anonymousSessionId);
        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("PASSKEY_ENROLLMENT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM remitters WHERE email_addr = ?", Integer.class, EMAIL))
                .isEqualTo(1);
    }

    @Test
    void codeIsStoredOnlyAsHash() throws Exception {
        startRegistration(new MockHttpSession(), EMAIL).andExpect(status().isAccepted());
        String code = lastCodeSentTo(EMAIL);

        String stored = jdbc.queryForObject("SELECT code_hash FROM email_verification_challenges", String.class);
        assertThat(stored).hasSize(64).doesNotContain(code);
    }

    @Test
    void enrollmentSessionCannotAccessRemitterApis() throws Exception {
        MockHttpSession session = enrollNewRemitter(EMAIL);

        mockMvc.perform(get("/api/passkeys").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void registrationForExistingAddressLooksTheSameButSendsNoCode() throws Exception {
        enrollNewRemitter(EMAIL);

        MockHttpSession attacker = new MockHttpSession();
        startRegistration(attacker, EMAIL)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(AuthController.ACCEPTED_MESSAGE));

        verify(mailSender).sendAlreadyRegisteredNotice(EMAIL);
        verify(mailSender, times(1)).sendVerificationCode(anyString(), anyString(), any());
        // Whatever code is guessed, the challenge has no code and can never be verified
        submitCode(attacker, "12345678").andExpect(status().isBadRequest());
    }

    // --- recovery ---

    @Test
    void recoveryForExistingRemitterStartsEnrollmentSession() throws Exception {
        enrollNewRemitter(EMAIL);

        MockHttpSession session = new MockHttpSession();
        startRecovery(session, EMAIL).andExpect(status().isAccepted());
        submitCode(session, lastCodeSentTo(EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("PASSKEY_ENROLLMENT"));
    }

    @Test
    void recoveryForUnknownAddressLooksTheSameButSendsNothing() throws Exception {
        MockHttpSession session = new MockHttpSession();
        startRecovery(session, "unknown@example.com")
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(AuthController.ACCEPTED_MESSAGE));

        verify(mailSender, never()).sendVerificationCode(anyString(), anyString(), any());
        submitCode(session, "12345678").andExpect(status().isBadRequest());
    }

    // --- code verification rules ---

    @Test
    void wrongCodeIsRejectedWithGenericMessage() throws Exception {
        MockHttpSession session = new MockHttpSession();
        startRegistration(session, EMAIL);
        String wrong = lastCodeSentTo(EMAIL).equals("00000000") ? "11111111" : "00000000";

        submitCode(session, wrong)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(AuthController.INVALID_CODE_MESSAGE));
        mockMvc.perform(get("/api/auth/session").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void codeIsLockedAfterMaxAttempts() throws Exception {
        MockHttpSession session = new MockHttpSession();
        startRegistration(session, EMAIL);
        String code = lastCodeSentTo(EMAIL);
        String wrong = code.equals("00000000") ? "11111111" : "00000000";

        for (int i = 0; i < 5; i++) {
            submitCode(session, wrong).andExpect(status().isBadRequest());
        }
        // Even the correct code no longer works
        submitCode(session, code).andExpect(status().isBadRequest());
    }

    @Test
    void correctCodeStillWorksOnTheLastAllowedAttempt() throws Exception {
        MockHttpSession session = new MockHttpSession();
        startRegistration(session, EMAIL);
        String code = lastCodeSentTo(EMAIL);
        String wrong = code.equals("00000000") ? "11111111" : "00000000";

        // max-attempts=5: four wrong attempts leave exactly one attempt
        for (int i = 0; i < 4; i++) {
            submitCode(session, wrong).andExpect(status().isBadRequest());
        }
        submitCode(session, code).andExpect(status().isOk());
    }

    @Test
    void codeCanBeUsedOnlyOnce() throws Exception {
        MockHttpSession session = new MockHttpSession();
        startRegistration(session, EMAIL);
        String code = lastCodeSentTo(EMAIL);
        submitCode(session, code).andExpect(status().isOk());

        submitCode(session, code).andExpect(status().isBadRequest());
    }

    @Test
    void expiredCodeIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        startRegistration(session, EMAIL);
        String code = lastCodeSentTo(EMAIL);
        jdbc.update("UPDATE email_verification_challenges SET expires_at = created_at");

        submitCode(session, code).andExpect(status().isBadRequest());
    }

    @Test
    void codeOnlyWorksInTheSessionThatRequestedIt() throws Exception {
        startRegistration(new MockHttpSession(), EMAIL);
        String code = lastCodeSentTo(EMAIL);

        // Another browser (e.g. an attacker who obtained the code) cannot use it
        submitCode(new MockHttpSession(), code).andExpect(status().isBadRequest());
    }

    @Test
    void newRequestReplacesThePreviousCode() throws Exception {
        MockHttpSession session = new MockHttpSession();
        startRegistration(session, EMAIL);
        String first = lastCodeSentTo(EMAIL);
        startRegistration(session, EMAIL);
        String second = lastCodeSentTo(EMAIL);

        if (!first.equals(second)) {
            submitCode(session, first).andExpect(status().isBadRequest());
        }
        submitCode(session, second).andExpect(status().isOk());
    }

    @Test
    void requestsPerAddressAreRateLimited() throws Exception {
        for (int i = 0; i < 3; i++) {
            startRegistration(new MockHttpSession(), EMAIL).andExpect(status().isAccepted());
        }
        MockHttpSession session = new MockHttpSession();
        // Same response, but no further e-mail is sent
        startRegistration(session, EMAIL).andExpect(status().isAccepted());

        verify(mailSender, times(3)).sendVerificationCode(anyString(), anyString(), any());
        submitCode(session, lastCodeSentTo(EMAIL)).andExpect(status().isBadRequest());
    }

    @Test
    void registrationAndRecoveryShareTheRateLimitOfAnAddress() throws Exception {
        // Mixing purposes must not give an attacker more codes (and more guesses) for one address
        startRegistration(new MockHttpSession(), EMAIL).andExpect(status().isAccepted());
        startRecovery(new MockHttpSession(), EMAIL).andExpect(status().isAccepted());
        startRegistration(new MockHttpSession(), EMAIL).andExpect(status().isAccepted());

        startRegistration(new MockHttpSession(), EMAIL).andExpect(status().isAccepted());

        // Only the two registrations sent a code (recovery of an unknown address sends nothing)
        verify(mailSender, times(2)).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void codesCanBeRequestedAgainAfterTheRateLimitWindow() throws Exception {
        for (int i = 0; i < 3; i++) {
            startRegistration(new MockHttpSession(), EMAIL).andExpect(status().isAccepted());
        }
        // request-window=1h: move the earlier requests out of the window
        jdbc.update("UPDATE email_verification_challenges SET created_at = created_at - INTERVAL '2' HOUR");
        MockHttpSession session = new MockHttpSession();

        startRegistration(session, EMAIL).andExpect(status().isAccepted());

        verify(mailSender, times(4)).sendVerificationCode(anyString(), anyString(), any());
        submitCode(session, lastCodeSentTo(EMAIL)).andExpect(status().isOk());
    }

    // --- input validation ---

    @Test
    void invalidEmailAddressIsRejected() throws Exception {
        postJson("/api/auth/registration", new MockHttpSession(),
                "{\"emailAddr\":\"not-an-email\",\"firstName\":\"Taro\",\"familyName\":\"Yamada\"}")
                .andExpect(status().isBadRequest());
        verify(mailSender, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    @Test
    void missingRequiredInputIsRejected() throws Exception {
        postJson("/api/auth/registration", new MockHttpSession(), "{\"emailAddr\":\"" + EMAIL + "\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/auth/email-verification", new MockHttpSession(), "{}").andExpect(status().isBadRequest());
    }

    @Test
    void nameWithControlCharactersOrTooLongIsRejected() throws Exception {
        postJson("/api/auth/registration", new MockHttpSession(),
                "{\"emailAddr\":\"" + EMAIL + "\",\"firstName\":\"Taro\\nInjected\",\"familyName\":\"Yamada\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/auth/registration", new MockHttpSession(),
                "{\"emailAddr\":\"" + EMAIL + "\",\"firstName\":\"" + "a".repeat(101) + "\",\"familyName\":\"Yamada\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedCodeIsRejected() throws Exception {
        postJson("/api/auth/email-verification", new MockHttpSession(), "{\"code\":\"12ab\"}")
                .andExpect(status().isBadRequest());
        postJson("/api/auth/email-verification", new MockHttpSession(), "{\"code\":")
                .andExpect(status().isBadRequest());
    }

    // --- CSRF ---

    @Test
    void publicPostEndpointsRequireCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/registration").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailAddr\":\"" + EMAIL + "\",\"firstName\":\"Taro\",\"familyName\":\"Yamada\"}"))
                .andExpect(status().isForbidden());
        verify(mailSender, never()).sendVerificationCode(anyString(), anyString(), any());
    }

    // --- session endpoint ---

    @Test
    void sessionEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
    }
}
