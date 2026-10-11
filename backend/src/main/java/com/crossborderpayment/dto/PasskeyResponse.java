package com.crossborderpayment.dto;

import java.time.Instant;

import org.springframework.security.web.webauthn.api.CredentialRecord;

/**
 * A passkey as shown to its owner. The public key and attestation data are not returned.
 */
public record PasskeyResponse(
        String id,
        String label,
        Instant createdAt,
        Instant lastUsedAt,
        boolean backupEligible,
        boolean backedUp) {

    public static PasskeyResponse from(CredentialRecord record) {
        return new PasskeyResponse(
                record.getCredentialId().toBase64UrlString(),
                record.getLabel(),
                record.getCreated(),
                record.getLastUsed(),
                record.isBackupEligible(),
                record.isBackupState());
    }
}
