package com.crossborderpayment.config;

import java.time.Duration;
import java.util.Set;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialParameters;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

import com.crossborderpayment.security.VerifyingUserCredentialRepository;

/**
 * Passkey (WebAuthn) Relying Party configuration.
 *
 * - User verification (biometrics / device PIN) is REQUIRED for registration and login, and the
 *   server rejects responses without the UV flag. A passkey login is therefore multi-factor
 *   (possession of the authenticator + something the user is / knows).
 * - Discoverable credentials (resident keys) are required, so login works without a username.
 * - Origins and RP ID are checked against an explicit server-side configuration.
 */
@Configuration
public class WebAuthnConfig {

    static final Duration CEREMONY_TIMEOUT = Duration.ofMinutes(5);

    @Bean
    public PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    @Bean
    public UserCredentialRepository userCredentialRepository(JdbcOperations jdbc) {
        return new VerifyingUserCredentialRepository(new JdbcUserCredentialRepository(jdbc));
    }

    @Bean
    public WebAuthnRelyingPartyOperations webAuthnRelyingPartyOperations(
            PublicKeyCredentialUserEntityRepository userEntities, UserCredentialRepository userCredentials,
            AuthProperties properties) {
        PublicKeyCredentialRpEntity rp = PublicKeyCredentialRpEntity.builder()
                .id(properties.webauthn().rpId())
                .name(properties.webauthn().rpName())
                .build();
        Webauthn4JRelyingPartyOperations operations = new Webauthn4JRelyingPartyOperations(userEntities,
                userCredentials, rp, Set.copyOf(properties.allowedOrigins()));
        operations.setCustomizeCreationOptions(options -> options
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .build())
                // Widely supported, modern algorithms only (ES256 first)
                .pubKeyCredParams(PublicKeyCredentialParameters.ES256, PublicKeyCredentialParameters.EdDSA,
                        PublicKeyCredentialParameters.RS256)
                .timeout(CEREMONY_TIMEOUT));
        operations.setCustomizeRequestOptions(options -> options
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(CEREMONY_TIMEOUT));
        return operations;
    }
}
