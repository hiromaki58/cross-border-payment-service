package com.crossborderpayment.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.crossborderpayment.service.AuthMailSender;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Additional rules for the passkey registration endpoints
 * (POST /api/auth/passkey/register/options and POST /api/auth/passkey/register):
 *
 * - only a remitter or a user in the e-mail-verified enrollment session may register a passkey;
 * - a remitter must have logged in with a passkey recently (re-authentication);
 * - registration errors return 400 without internal details;
 * - after a successful registration the user is notified by e-mail, and an enrollment session
 *   is ended so that the user must log in with the new passkey.
 */
public class PasskeyRegistrationGuardFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PasskeyRegistrationGuardFilter.class);

    private static final String ROLE_REMITTER = "ROLE_" + Roles.REMITTER;
    private static final String ROLE_PASSKEY_ENROLLMENT = "ROLE_" + Roles.PASSKEY_ENROLLMENT;

    private final RequestMatcher optionsMatcher = PasskeyEndpoints.post(PasskeyEndpoints.REGISTER_OPTIONS);
    private final RequestMatcher registerMatcher = PasskeyEndpoints.post(PasskeyEndpoints.REGISTER);

    private final AuthSessionManager authSessionManager;
    private final AuthMailSender mailSender;
    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
            .getContextHolderStrategy();

    public PasskeyRegistrationGuardFilter(AuthSessionManager authSessionManager, AuthMailSender mailSender) {
        this.authSessionManager = authSessionManager;
        this.mailSender = mailSender;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !optionsMatcher.matches(request) && !registerMatcher.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();
        if (!trustResolver.isAuthenticated(authentication)) {
            ProblemResponses.write(response, HttpStatus.UNAUTHORIZED, "Authentication is required.", null);
            return;
        }
        boolean enrollment = hasAuthority(authentication, ROLE_PASSKEY_ENROLLMENT);
        boolean remitter = hasAuthority(authentication, ROLE_REMITTER);
        if (!enrollment && !remitter) {
            ProblemResponses.write(response, HttpStatus.FORBIDDEN, "Access is denied.", null);
            return;
        }
        if (!enrollment && !authSessionManager.isRecentlyAuthenticated(request)) {
            ProblemResponses.write(response, HttpStatus.FORBIDDEN,
                    ProblemResponses.REAUTHENTICATION_REQUIRED_DETAIL, ProblemResponses.REAUTHENTICATION_REQUIRED);
            return;
        }

        if (optionsMatcher.matches(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            filterChain.doFilter(request, response);
        } catch (RuntimeException ex) {
            // e.g. signature / origin / challenge verification failed, invalid label, duplicate credential
            log.warn("Passkey registration failed: {}", ex.getClass().getSimpleName());
            if (!response.isCommitted()) {
                response.resetBuffer();
                ProblemResponses.write(response, HttpStatus.BAD_REQUEST, "The passkey could not be registered.", null);
            }
            return;
        }

        if (response.getStatus() == HttpStatus.OK.value()) {
            log.info("Passkey registered (enrollment session: {})", enrollment);
            mailSender.sendPasskeyAddedNotice(authentication.getName());
            if (enrollment) {
                endEnrollmentSession(request);
            }
        }
    }

    // The enrollment session only exists to register a passkey; the user now logs in with it
    private void endEnrollmentSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        securityContextHolderStrategy.clearContext();
    }

    private static boolean hasAuthority(Authentication authentication, String authority) {
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
