package com.crossborderpayment.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.HttpMessageConverterAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * After a successful passkey login: records the login time (absolute lifetime / re-authentication)
 * and returns Spring Security's JSON response ({"authenticated":true,"redirectUrl":"/"}).
 */
public class PasskeyLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthSessionManager authSessionManager;
    private final HttpMessageConverterAuthenticationSuccessHandler delegate =
            new HttpMessageConverterAuthenticationSuccessHandler();

    public PasskeyLoginSuccessHandler(AuthSessionManager authSessionManager) {
        this.authSessionManager = authSessionManager;
        // Never redirect to a URL saved from an earlier request
        this.delegate.setRequestCache(new NullRequestCache());
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        authSessionManager.onPasskeyLogin(request);
        delegate.onAuthenticationSuccess(request, response, authentication);
    }
}
