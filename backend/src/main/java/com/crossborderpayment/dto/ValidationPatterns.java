package com.crossborderpayment.dto;

public final class ValidationPatterns {

    // Rejects control characters (e.g. line breaks), which are never valid in names
    public static final String NO_CONTROL_CHARACTERS = "^[^\\p{Cntrl}]*$";

    // Base64url credential id (WebAuthn allows up to 1023 bytes = 1364 characters)
    public static final String CREDENTIAL_ID = "^[A-Za-z0-9_-]{1,1364}$";

    private ValidationPatterns() {
    }
}
