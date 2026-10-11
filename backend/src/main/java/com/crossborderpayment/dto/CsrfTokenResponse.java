package com.crossborderpayment.dto;

/**
 * Tells the SPA where the CSRF token is: read the cookie and send its value unchanged in the header.
 * The token itself is not returned in the body (only the cookie value is accepted in the header).
 */
public record CsrfTokenResponse(String cookieName, String headerName) {
}
