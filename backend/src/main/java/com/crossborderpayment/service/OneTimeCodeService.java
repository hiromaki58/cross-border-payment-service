package com.crossborderpayment.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.crossborderpayment.config.AuthProperties;

/**
 * Generates one-time codes and their keyed hashes.
 *
 * - 8 random digits from SecureRandom (about 26.6 bits; together with the attempt limit and short
 *   lifetime this keeps online guessing impractical).
 * - Stored as HMAC-SHA256 bound to the purpose and address, so a leaked database row cannot be
 *   brute-forced offline without the key, and a code cannot be reused for another address / purpose.
 */
@Service
public class OneTimeCodeService {

    static final int CODE_DIGITS = 8;
    private static final int CODE_BOUND = 100_000_000;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_BYTES = 32;

    private static final Logger log = LoggerFactory.getLogger(OneTimeCodeService.class);

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec key;

    public OneTimeCodeService(AuthProperties properties) {
        this.key = new SecretKeySpec(loadKey(properties.emailVerification().hmacKey()), HMAC_ALGORITHM);
    }

    public String generateCode() {
        return String.format("%0" + CODE_DIGITS + "d", secureRandom.nextInt(CODE_BOUND));
    }

    public String hash(String purpose, String emailAddr, String code) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((purpose + ":" + emailAddr + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC is not available", ex);
        }
    }

    // Constant-time comparison so that response timing does not reveal how much of a code matched
    public boolean matches(String expectedHash, String purpose, String emailAddr, String code) {
        byte[] expected = expectedHash.getBytes(StandardCharsets.US_ASCII);
        byte[] actual = hash(purpose, emailAddr, code).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] loadKey(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            log.warn("app.security.email-verification.hmac-key is not set; using a random key for this process only. "
                    + "Set APP_OTP_HMAC_KEY in production (the same value on every instance).");
            byte[] random = new byte[MIN_KEY_BYTES];
            secureRandom.nextBytes(random);
            return random;
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(configuredKey.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("app.security.email-verification.hmac-key must be Base64");
        }
        if (decoded.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("app.security.email-verification.hmac-key must be at least 32 bytes");
        }
        return decoded;
    }
}
