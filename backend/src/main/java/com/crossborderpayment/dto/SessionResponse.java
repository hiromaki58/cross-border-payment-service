package com.crossborderpayment.dto;

/**
 * Authentication state of the current session.
 *
 * @param stage AUTHENTICATED (logged in with a passkey) or PASSKEY_ENROLLMENT (e-mail verified;
 *              a passkey must be registered next)
 */
public record SessionResponse(String stage) {

    public static final String AUTHENTICATED = "AUTHENTICATED";
    public static final String PASSKEY_ENROLLMENT = "PASSKEY_ENROLLMENT";
}
