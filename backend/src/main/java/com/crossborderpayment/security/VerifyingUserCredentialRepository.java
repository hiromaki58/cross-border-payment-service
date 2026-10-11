package com.crossborderpayment.security;

import java.util.List;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

/**
 * Adds checks that Spring Security's passkey handling does not perform itself:
 *
 * - Signature counter: on each login the counter reported by the authenticator must be greater than
 *   the stored value (unless both are 0, as with most synced passkeys). A counter that does not
 *   increase can indicate a cloned authenticator, so the login is rejected.
 * - Label: the user-supplied passkey name must be short, non-blank and free of control characters.
 *
 * Throwing from save() makes the login fail (BadCredentialsException) or the registration fail (400).
 */
public class VerifyingUserCredentialRepository implements UserCredentialRepository {

    static final int MAX_LABEL_LENGTH = 64;
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    private final UserCredentialRepository delegate;

    public VerifyingUserCredentialRepository(UserCredentialRepository delegate) {
        this.delegate = delegate;
    }

    @Override
    public void save(CredentialRecord credentialRecord) {
        CredentialRecord existing = delegate.findByCredentialId(credentialRecord.getCredentialId());
        if (existing == null) {
            validateLabel(credentialRecord.getLabel());
        } else {
            verifySignatureCounter(existing.getSignatureCount(), credentialRecord.getSignatureCount());
        }
        delegate.save(credentialRecord);
    }

    @Override
    public void delete(Bytes credentialId) {
        delegate.delete(credentialId);
    }

    @Override
    public @Nullable CredentialRecord findByCredentialId(Bytes credentialId) {
        return delegate.findByCredentialId(credentialId);
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes userId) {
        return delegate.findByUserId(userId);
    }

    static void verifySignatureCounter(long stored, long presented) {
        // WebAuthn Level 3, 7.2 step 22: if either value is non-zero, the new value must be greater
        if ((stored != 0 || presented != 0) && presented <= stored) {
            throw new IllegalStateException("Signature counter did not increase; the authenticator may be cloned");
        }
    }

    static void validateLabel(String label) {
        if (label == null || label.isBlank() || label.length() > MAX_LABEL_LENGTH
                || CONTROL_CHARACTERS.matcher(label).find()) {
            throw new IllegalArgumentException("Invalid passkey label");
        }
    }
}
