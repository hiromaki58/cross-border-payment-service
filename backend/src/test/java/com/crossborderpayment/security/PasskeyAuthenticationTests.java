package com.crossborderpayment.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.crossborderpayment.support.AuthIntegrationTestSupport;
import com.crossborderpayment.support.PasskeyTestClient;

import tools.jackson.databind.JsonNode;

/**
 * Passkey registration and login end-to-end, with an emulated authenticator.
 */
class PasskeyAuthenticationTests extends AuthIntegrationTestSupport {

    private static final String EMAIL = "taro@example.com";

    // --- registration ---

    @Test
    void registrationOptionsRequireUserVerificationAndDiscoverableCredential() throws Exception {
        MockHttpSession enrollment = enrollNewRemitter(EMAIL);

        JsonNode options = new PasskeyTestClient(mockMvc).registrationOptions(enrollment);

        assertThat(options.get("authenticatorSelection").get("userVerification").asString()).isEqualTo("required");
        assertThat(options.get("authenticatorSelection").get("residentKey").asString()).isEqualTo("required");
        assertThat(options.get("rp").get("id").asString()).isEqualTo("localhost");
        assertThat(options.get("user").get("name").asString()).isEqualTo(EMAIL);
    }

    @Test
    void enrollmentSessionRegistersPasskeyAndIsThenEnded() throws Exception {
        MockHttpSession enrollment = enrollNewRemitter(EMAIL);

        new PasskeyTestClient(mockMvc).register(enrollment, "My phone")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertThat(jdbc.queryForObject("SELECT label FROM user_credentials", String.class)).isEqualTo("My phone");
        verify(mailSender).sendPasskeyAddedNotice(EMAIL);
        // The user has to log in with the new passkey
        assertThat(enrollment.isInvalid()).isTrue();
    }

    @Test
    void anonymousUserCannotRegisterPasskey() throws Exception {
        new PasskeyTestClient(mockMvc).requestRegistrationOptions(new MockHttpSession())
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/passkey/register").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidLabelIsRejected() throws Exception {
        MockHttpSession enrollment = enrollNewRemitter(EMAIL);

        new PasskeyTestClient(mockMvc).register(enrollment, "x".repeat(65))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The passkey could not be registered."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_credentials", Integer.class)).isZero();
    }

    @Test
    void registrationFromUnexpectedOriginIsRejected() throws Exception {
        MockHttpSession enrollment = enrollNewRemitter(EMAIL);

        new PasskeyTestClient(mockMvc, "https://evil.example").register(enrollment, "My phone")
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_credentials", Integer.class)).isZero();
    }

    @Test
    void springDefaultWebAuthnUrlsAreNotAvailable() throws Exception {
        PasskeyTestClient passkeys = new PasskeyTestClient(mockMvc);
        MockHttpSession session = registerAndLogin(EMAIL, passkeys);
        String credentialId = jdbc.queryForObject("SELECT credential_id FROM user_credentials", String.class);

        // Only the /api/auth/passkey/... URLs exist; the defaults are not processed by any filter
        mockMvc.perform(post("/webauthn/authenticate/options").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/login/webauthn").with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/webauthn/register/options").session(session).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/webauthn/register/" + credentialId).session(session).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_credentials", Integer.class)).isEqualTo(1);
    }

    // --- login ---

    @Test
    void passkeyLoginGrantsFullAccessAndRotatesSessionId() throws Exception {
        PasskeyTestClient passkeys = new PasskeyTestClient(mockMvc);
        passkeys.register(enrollNewRemitter(EMAIL), "My phone").andExpect(status().isOk());

        MockHttpSession session = new MockHttpSession();
        // Anonymous session created by the options request
        mockMvc.perform(post("/api/auth/passkey/login/options").session(session).with(csrf()));
        String anonymousSessionId = session.getId();
        passkeys.login(session)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true));

        assertThat(session.getId()).isNotEqualTo(anonymousSessionId);
        mockMvc.perform(get("/api/auth/session").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("AUTHENTICATED"));
        mockMvc.perform(get("/api/passkeys").session(session)).andExpect(status().isOk());
    }

    @Test
    void loginWithoutUserVerificationIsRejected() throws Exception {
        PasskeyTestClient passkeys = new PasskeyTestClient(mockMvc);
        passkeys.register(enrollNewRemitter(EMAIL), "My phone").andExpect(status().isOk());

        MockHttpSession session = new MockHttpSession();
        passkeys.login(session, false).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/passkeys").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void loginFromUnexpectedOriginIsRejected() throws Exception {
        PasskeyTestClient passkeys = new PasskeyTestClient(mockMvc);
        passkeys.register(enrollNewRemitter(EMAIL), "My phone").andExpect(status().isOk());

        // The same passkey used on a phishing site produces a signature for the wrong origin
        passkeys.setOrigin("https://evil.example");
        MockHttpSession session = new MockHttpSession();
        passkeys.login(session).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/passkeys").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void signatureCounterThatDoesNotIncreaseIsRejected() throws Exception {
        PasskeyTestClient passkeys = new PasskeyTestClient(mockMvc);
        passkeys.register(enrollNewRemitter(EMAIL), "My phone").andExpect(status().isOk());
        passkeys.login(new MockHttpSession()).andExpect(status().isOk());

        // Simulates a cloned authenticator: the stored counter is ahead of the authenticator's counter
        jdbc.update("UPDATE user_credentials SET signature_count = 1000");

        passkeys.login(new MockHttpSession()).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutCsrfTokenIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/passkey/login").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void malformedLoginRequestIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/passkey/login/options").session(session).with(csrf()));
        mockMvc.perform(post("/api/auth/passkey/login").session(session).with(csrf())
                        .contentType("application/json").content("{\"id\":"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithoutRequestingOptionsIsRejected() throws Exception {
        mockMvc.perform(post("/api/auth/passkey/login").with(csrf()).contentType("application/json")
                        .content("{\"id\":\"AA\",\"rawId\":\"AA\",\"type\":\"public-key\",\"response\":{}}"))
                .andExpect(status().isUnauthorized());
    }

    // --- adding a passkey while logged in (re-authentication) ---

    @Test
    void remitterCanAddPasskeyRightAfterLogin() throws Exception {
        PasskeyTestClient passkeys = new PasskeyTestClient(mockMvc);
        MockHttpSession session = registerAndLogin(EMAIL, passkeys);

        new PasskeyTestClient(mockMvc).register(session, "Second key").andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_credentials", Integer.class)).isEqualTo(2);
        // A full session is not ended by adding a passkey
        mockMvc.perform(get("/api/passkeys").session(session)).andExpect(status().isOk());
    }

    @Test
    void addingPasskeyRequiresRecentLogin() throws Exception {
        MockHttpSession session = registerAndLogin(EMAIL, new PasskeyTestClient(mockMvc));
        session.setAttribute(AuthSessionManager.AUTHENTICATED_AT, Instant.now().minus(Duration.ofMinutes(6)));

        new PasskeyTestClient(mockMvc).requestRegistrationOptions(session)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ProblemResponses.REAUTHENTICATION_REQUIRED));
    }

    // --- session lifetime and logout ---

    @Test
    void sessionExpiresAfterAbsoluteLifetime() throws Exception {
        MockHttpSession session = registerAndLogin(EMAIL, new PasskeyTestClient(mockMvc));
        session.setAttribute(AuthSessionManager.EXPIRES_AT, Instant.now().minusSeconds(1));

        mockMvc.perform(get("/api/passkeys").session(session)).andExpect(status().isUnauthorized());
        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    void enrollmentSessionHasShortLifetime() throws Exception {
        MockHttpSession session = enrollNewRemitter(EMAIL);
        Instant expiresAt = (Instant) session.getAttribute(AuthSessionManager.EXPIRES_AT);

        assertThat(expiresAt).isBefore(Instant.now().plus(Duration.ofMinutes(11)));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = registerAndLogin(EMAIL, new PasskeyTestClient(mockMvc));

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        mockMvc.perform(get("/api/passkeys").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRequiresCsrfToken() throws Exception {
        MockHttpSession session = registerAndLogin(EMAIL, new PasskeyTestClient(mockMvc));

        mockMvc.perform(post("/api/auth/logout").session(session)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/passkeys").session(session)).andExpect(status().isOk());
    }

    // --- response headers ---

    @Test
    void responsesCarrySecurityHeaders() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }
}
