package com.crossborderpayment.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;

import com.crossborderpayment.config.AuthProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Keeps track of when and how the current session was authenticated.
 *
 * - Absolute lifetime: a session expires a fixed time after login, even if it is used continuously.
 * - Recent authentication: changing passkeys requires a login within a short window.
 * Both are recorded as server-side session attributes, so the client cannot change them.
 */
@Component
public class AuthSessionManager {

    static final String AUTHENTICATED_AT = AuthSessionManager.class.getName() + ".AUTHENTICATED_AT";
    static final String EXPIRES_AT = AuthSessionManager.class.getName() + ".EXPIRES_AT";

    private final AuthProperties properties;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
            .getContextHolderStrategy();

    public AuthSessionManager(AuthProperties properties, SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository) {
        this.properties = properties;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Called after a successful passkey login (the session id has already been changed).
     */
    public void onPasskeyLogin(HttpServletRequest request) {
        mark(request, properties.session().absoluteTimeout());
    }

    /**
     * Logs the user in with a limited session that may only register a passkey
     * (after the e-mail address was verified with a one-time code).
     */
    public void startEnrollmentSession(String username, HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(username, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + Roles.PASSKEY_ENROLLMENT)));
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
        mark(request, properties.session().enrollmentTimeout());
    }

    public boolean isExpired(HttpSession session) {
        Object expiresAt = session.getAttribute(EXPIRES_AT);
        return expiresAt instanceof Instant instant && !Instant.now().isBefore(instant);
    }

    public boolean isRecentlyAuthenticated(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object authenticatedAt = session.getAttribute(AUTHENTICATED_AT);
        return authenticatedAt instanceof Instant instant
                && Instant.now().isBefore(instant.plus(properties.session().reauthenticationWindow()));
    }

    private void mark(HttpServletRequest request, Duration lifetime) {
        HttpSession session = request.getSession();
        Instant now = Instant.now();
        session.setAttribute(AUTHENTICATED_AT, now);
        session.setAttribute(EXPIRES_AT, now.plus(lifetime));
        // The CSRF token was replaced on login; render the new one so the SPA receives its cookie now
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
    }
}
