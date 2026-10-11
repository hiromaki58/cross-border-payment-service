package com.crossborderpayment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import com.crossborderpayment.support.AuthIntegrationTestSupport;

import jakarta.servlet.http.Cookie;

/**
 * The real cookie-based CSRF protection used by the SPA.
 *
 * Runs in its own application context and never uses spring-security-test's csrf(), which
 * replaces the CSRF token repository of the shared context.
 */
@TestPropertySource(properties = "test.context=real-csrf")
class CsrfCookieTests extends AuthIntegrationTestSupport {

    private static final String EMAIL = "taro@example.com";

    private MvcResult fetchCsrfToken(MockHttpSession session) throws Exception {
        return mockMvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cookieName").value("XSRF-TOKEN"))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(cookie().secure("XSRF-TOKEN", true))
                .andExpect(cookie().sameSite("XSRF-TOKEN", "Strict"))
                .andReturn();
    }

    private MvcResult postWithToken(String url, MockHttpSession session, Cookie csrfCookie, String json)
            throws Exception {
        return mockMvc.perform(post(url).session(session).cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn();
    }

    private static Cookie lastCookie(MvcResult result, String name) {
        Cookie last = null;
        for (Cookie cookie : result.getResponse().getCookies()) {
            if (name.equals(cookie.getName())) {
                last = cookie;
            }
        }
        return last;
    }

    @Test
    void tokenFromCookieIsAcceptedInHeader() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Cookie csrfCookie = fetchCsrfToken(session).getResponse().getCookie("XSRF-TOKEN");

        MvcResult result = postWithToken("/api/auth/recovery", session, csrfCookie,
                "{\"emailAddr\":\"" + EMAIL + "\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(202);
    }

    @Test
    void maskedTokenFromRequestAttributeIsNotAcceptedInHeader() throws Exception {
        // Only the raw cookie value is valid in the header (Spring Security SPA CSRF handling)
        MockHttpSession session = new MockHttpSession();
        Cookie csrfCookie = fetchCsrfToken(session).getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/auth/recovery").session(session).cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue() + "x")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emailAddr\":\"" + EMAIL + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void requestWithoutOrWithWrongTokenIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Cookie csrfCookie = fetchCsrfToken(session).getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/auth/recovery").session(session).cookie(csrfCookie)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emailAddr\":\"" + EMAIL + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/recovery").session(session).cookie(csrfCookie).header("X-XSRF-TOKEN", "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"emailAddr\":\"" + EMAIL + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tokenIsReplacedWhenTheUserIsAuthenticated() throws Exception {
        MockHttpSession session = new MockHttpSession();
        Cookie before = fetchCsrfToken(session).getResponse().getCookie("XSRF-TOKEN");
        postWithToken("/api/auth/registration", session, before,
                "{\"emailAddr\":\"" + EMAIL + "\",\"firstName\":\"Taro\",\"familyName\":\"Yamada\"}");
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailSender).sendVerificationCode(eq(EMAIL), code.capture(), any(Duration.class));

        MvcResult verified = postWithToken("/api/auth/email-verification", session, before,
                "{\"code\":\"" + code.getValue() + "\"}");
        assertThat(verified.getResponse().getStatus()).isEqualTo(200);

        // A new token is issued with the login response (after the cookie that clears the old one;
        // browsers apply Set-Cookie headers in order); the old token no longer works
        Cookie after = lastCookie(verified, "XSRF-TOKEN");
        assertThat(after).isNotNull();
        assertThat(after.getValue()).isNotEmpty().isNotEqualTo(before.getValue());
        // The browser now holds the new cookie, so a request carrying the old token value is rejected
        mockMvc.perform(post("/api/auth/logout").session(session).cookie(after).header("X-XSRF-TOKEN", before.getValue()))
                .andExpect(status().isForbidden());
        MvcResult withNewToken = postWithToken("/api/auth/logout", session, after, "{}");
        assertThat(withNewToken.getResponse().getStatus()).isEqualTo(204);
    }
}
