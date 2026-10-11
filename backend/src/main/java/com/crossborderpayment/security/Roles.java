package com.crossborderpayment.security;

/**
 * Role names (without the "ROLE_" prefix, as used by hasRole / hasAnyRole).
 */
public final class Roles {

    // Fully logged in with a passkey
    public static final String REMITTER = "REMITTER";

    // E-mail address verified with a one-time code; may only register a passkey
    public static final String PASSKEY_ENROLLMENT = "PASSKEY_ENROLLMENT";

    private Roles() {
    }
}
