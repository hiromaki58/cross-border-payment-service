package com.crossborderpayment.security;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsFilter;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationProvider;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsFilter;
import org.springframework.security.web.webauthn.registration.WebAuthnRegistrationFilter;

/**
 * Passkey (WebAuthn) endpoints under /api, so the frontend proxy and the load balancer only need to
 * route /api to the backend.
 *
 * Spring Security's http.webAuthn() only allows changing the login URL, so the same Spring Security
 * filters are created here with explicit URLs. The ceremony logic (challenge storage in the session,
 * signature / origin / RP ID verification) is unchanged.
 */
public final class PasskeyEndpoints {

    public static final String LOGIN_OPTIONS = "/api/auth/passkey/login/options";
    public static final String LOGIN = "/api/auth/passkey/login";
    public static final String REGISTER_OPTIONS = "/api/auth/passkey/register/options";
    public static final String REGISTER = "/api/auth/passkey/register";

    // Spring's credential deletion endpoint is replaced by DELETE /api/passkeys/{id}; never match it
    private static final RequestMatcher NO_REQUEST = request -> false;

    private PasskeyEndpoints() {
    }

    static RequestMatcher post(String path) {
        return PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, path);
    }

    /**
     * POST LOGIN_OPTIONS: returns the challenge for navigator.credentials.get()
     */
    public static PublicKeyCredentialRequestOptionsFilter loginOptionsFilter(WebAuthnRelyingPartyOperations rpOperations) {
        PublicKeyCredentialRequestOptionsFilter filter = new PublicKeyCredentialRequestOptionsFilter(rpOperations);
        filter.setRequestMatcher(post(LOGIN_OPTIONS));
        return filter;
    }

    /**
     * POST LOGIN: verifies the assertion and logs the remitter in
     * (new session id and CSRF token, login time recorded, events published for the audit log).
     */
    public static WebAuthnAuthenticationFilter loginFilter(WebAuthnRelyingPartyOperations rpOperations,
            UserDetailsService userDetailsService, AuthenticationEventPublisher eventPublisher,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository, AuthSessionManager authSessionManager) {
        ProviderManager authenticationManager = new ProviderManager(
                new WebAuthnAuthenticationProvider(rpOperations, userDetailsService));
        authenticationManager.setAuthenticationEventPublisher(eventPublisher);

        WebAuthnAuthenticationFilter filter = new WebAuthnAuthenticationFilter();
        filter.setRequiresAuthenticationRequestMatcher(post(LOGIN));
        filter.setAuthenticationManager(authenticationManager);
        // Not applied by Spring's default WebAuthn setup: prevents session fixation
        filter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy);
        filter.setSecurityContextRepository(securityContextRepository);
        filter.setAuthenticationSuccessHandler(new PasskeyLoginSuccessHandler(authSessionManager));
        filter.setAuthenticationFailureHandler(
                new AuthenticationEntryPointFailureHandler(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return filter;
    }

    /**
     * POST REGISTER_OPTIONS: returns the challenge for navigator.credentials.create()
     */
    public static PublicKeyCredentialCreationOptionsFilter registerOptionsFilter(
            WebAuthnRelyingPartyOperations rpOperations) {
        PublicKeyCredentialCreationOptionsFilter filter = new PublicKeyCredentialCreationOptionsFilter(rpOperations);
        filter.setRequestMatcher(post(REGISTER_OPTIONS));
        return filter;
    }

    /**
     * POST REGISTER: verifies and stores the new passkey
     */
    public static WebAuthnRegistrationFilter registerFilter(UserCredentialRepository userCredentials,
            WebAuthnRelyingPartyOperations rpOperations) {
        WebAuthnRegistrationFilter filter = new WebAuthnRegistrationFilter(userCredentials, rpOperations);
        filter.setRegisterCredentialMatcher(post(REGISTER));
        filter.setRemoveCredentialMatcher(NO_REQUEST);
        return filter;
    }
}
