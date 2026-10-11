package com.crossborderpayment.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.crossborderpayment.service.AuthMailSender;

/**
 * Base class for end-to-end authentication tests: real security filter chain, database and
 * passkey verification; only e-mail sending is mocked (to read the one-time code).
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AuthIntegrationTestSupport {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbc;

    @MockitoBean
    protected AuthMailSender mailSender;

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM user_credentials");
        jdbc.update("DELETE FROM user_entities");
        jdbc.update("DELETE FROM email_verification_challenges");
        jdbc.update("DELETE FROM remitters");
    }

    protected ResultActions postJson(String url, MockHttpSession session, String json) throws Exception {
        return mockMvc.perform(post(url).session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions startRegistration(MockHttpSession session, String emailAddr) throws Exception {
        return postJson("/api/auth/registration", session,
                "{\"emailAddr\":\"" + emailAddr + "\",\"firstName\":\"Taro\",\"familyName\":\"Yamada\"}");
    }

    protected ResultActions startRecovery(MockHttpSession session, String emailAddr) throws Exception {
        return postJson("/api/auth/recovery", session, "{\"emailAddr\":\"" + emailAddr + "\"}");
    }

    protected ResultActions submitCode(MockHttpSession session, String code) throws Exception {
        return postJson("/api/auth/email-verification", session, "{\"code\":\"" + code + "\"}");
    }

    // The most recent code e-mailed to the address
    protected String lastCodeSentTo(String emailAddr) {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailSender, atLeastOnce()).sendVerificationCode(eq(emailAddr), code.capture(), any(Duration.class));
        return code.getValue();
    }

    /**
     * New registration up to the enrollment session (e-mail verified, passkey not registered yet).
     */
    protected MockHttpSession enrollNewRemitter(String emailAddr) throws Exception {
        MockHttpSession session = new MockHttpSession();
        startRegistration(session, emailAddr).andExpect(status().isAccepted());
        submitCode(session, lastCodeSentTo(emailAddr)).andExpect(status().isOk());
        return session;
    }

    /**
     * Full registration and passkey login; returns the logged-in session.
     */
    protected MockHttpSession registerAndLogin(String emailAddr, PasskeyTestClient passkeys) throws Exception {
        MockHttpSession enrollment = enrollNewRemitter(emailAddr);
        passkeys.register(enrollment, "My passkey").andExpect(status().isOk());
        MockHttpSession session = new MockHttpSession();
        passkeys.login(session).andExpect(status().isOk());
        return session;
    }
}
