package com.crossborderpayment.model;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/**
 * A one-time code sent by e-mail. Only a keyed hash of the code is stored.
 * codeHash is null when no code was sent, so such a challenge can never be verified.
 */
@Entity
@Table(name = "email_verification_challenges")
public class EmailVerificationChallengeModel extends BaseModel {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EmailVerificationPurpose purpose;

    @Column(nullable = false)
    private String emailAddr;

    // Only for REGISTRATION: used to create the remitter once the address is verified
    private String firstName;

    private String familyName;

    @Column(length = 64)
    private String codeHash;

    @Column(nullable = false)
    private int attemptCount;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant consumedAt;

    protected EmailVerificationChallengeModel() {
    }

    public EmailVerificationChallengeModel(EmailVerificationPurpose purpose, String emailAddr, String firstName,
            String familyName, String codeHash, Instant createdAt, Instant expiresAt) {
        this.purpose = purpose;
        this.emailAddr = EmailAddressNormalizer.normalize(emailAddr);
        this.firstName = firstName;
        this.familyName = familyName;
        this.codeHash = codeHash;
        this.attemptCount = 0;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Whether a code may still be checked against this challenge.
     */
    public boolean isUsable(Instant now, int maxAttempts) {
        return codeHash != null && consumedAt == null && now.isBefore(expiresAt) && attemptCount < maxAttempts;
    }

    public void recordAttempt() {
        attemptCount++;
    }

    public void markConsumed(Instant now) {
        consumedAt = now;
    }

    public EmailVerificationPurpose getPurpose() {
        return purpose;
    }

    public String getEmailAddr() {
        return emailAddr;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getFamilyName() {
        return familyName;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
