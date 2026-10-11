package com.crossborderpayment.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.stereotype.Component;

/**
 * Security audit log for passkey logins.
 *
 * Logs a pseudonymous identifier (the random WebAuthn user handle) instead of the e-mail address,
 * and only the failure type, never exception messages or request data (avoids PII and log injection).
 */
@Component
public class AuthenticationAuditListener {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationAuditListener.class);

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        log.info("Passkey login succeeded: userHandle={}", userHandle(event.getAuthentication()));
    }

    @EventListener
    public void onFailure(AbstractAuthenticationFailureEvent event) {
        log.warn("Passkey login failed: reason={}", event.getException().getClass().getSimpleName());
    }

    private static String userHandle(Authentication authentication) {
        if (authentication.getPrincipal() instanceof PublicKeyCredentialUserEntity userEntity) {
            return userEntity.getId().toBase64UrlString();
        }
        return "unknown";
    }
}
