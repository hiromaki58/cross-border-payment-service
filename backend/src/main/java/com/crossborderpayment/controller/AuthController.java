package com.crossborderpayment.controller;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.crossborderpayment.config.SessionSecurityConfig;
import com.crossborderpayment.dto.CsrfTokenResponse;
import com.crossborderpayment.dto.EmailVerificationRequest;
import com.crossborderpayment.dto.MessageResponse;
import com.crossborderpayment.dto.RecoveryRequest;
import com.crossborderpayment.dto.RegistrationRequest;
import com.crossborderpayment.dto.SessionResponse;
import com.crossborderpayment.security.AuthSessionManager;
import com.crossborderpayment.security.Roles;
import com.crossborderpayment.service.EmailVerificationService;
import com.crossborderpayment.service.EmailVerificationService.VerifiedEmail;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

/**
 * Login support endpoints. The passkey ceremonies themselves are handled by Spring Security's
 * filters at the URLs in PasskeyEndpoints (/api/auth/passkey/...).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // The pending challenge is bound to the session that requested it, so a code only works in that browser
    static final String CHALLENGE_ATTRIBUTE = AuthController.class.getName() + ".EMAIL_VERIFICATION_CHALLENGE";

    // Same response whether or not the address is registered (no account enumeration)
    static final String ACCEPTED_MESSAGE = "If the e-mail address can be used, a verification code has been sent.";
    static final String INVALID_CODE_MESSAGE = "The verification code is invalid or has expired.";

    private final EmailVerificationService emailVerificationService;
    private final AuthSessionManager authSessionManager;

    public AuthController(EmailVerificationService emailVerificationService, AuthSessionManager authSessionManager) {
        this.emailVerificationService = emailVerificationService;
        this.authSessionManager = authSessionManager;
    }

    /**
     * Issues the CSRF cookie before the SPA's first POST (and after login, when the token is replaced).
     * The frontend sends the cookie value unchanged in the X-XSRF-TOKEN header.
     */
    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(CsrfToken csrfToken) {
        // Accessing the token makes Spring Security write the cookie
        csrfToken.getToken();
        return new CsrfTokenResponse(SessionSecurityConfig.CSRF_COOKIE_NAME, SessionSecurityConfig.CSRF_HEADER_NAME);
    }

    @PostMapping("/registration")
    public ResponseEntity<MessageResponse> startRegistration(@Valid @RequestBody RegistrationRequest body,
            HttpServletRequest request) {
        bindChallenge(request, emailVerificationService.requestRegistration(body.emailAddr(), body.firstName(),
                body.familyName()));
        return ResponseEntity.accepted().body(new MessageResponse(ACCEPTED_MESSAGE));
    }

    @PostMapping("/recovery")
    public ResponseEntity<MessageResponse> startRecovery(@Valid @RequestBody RecoveryRequest body,
            HttpServletRequest request) {
        bindChallenge(request, emailVerificationService.requestRecovery(body.emailAddr()));
        return ResponseEntity.accepted().body(new MessageResponse(ACCEPTED_MESSAGE));
    }

    /**
     * Verifies the code; on success the session may register a passkey (and nothing else).
     */
    @PostMapping("/email-verification")
    public ResponseEntity<?> verifyEmail(@Valid @RequestBody EmailVerificationRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        Long challengeId = (session != null && session.getAttribute(CHALLENGE_ATTRIBUTE) instanceof Long id) ? id : null;

        Optional<VerifiedEmail> verified = emailVerificationService.verify(challengeId, body.code());
        if (verified.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, INVALID_CODE_MESSAGE));
        }
        session.removeAttribute(CHALLENGE_ATTRIBUTE);
        authSessionManager.startEnrollmentSession(verified.get().emailAddr(), request, response);
        return ResponseEntity.ok(new SessionResponse(SessionResponse.PASSKEY_ENROLLMENT));
    }

    @GetMapping("/session")
    public SessionResponse session(Authentication authentication) {
        boolean remitter = authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + Roles.REMITTER).equals(authority.getAuthority()));
        return new SessionResponse(remitter ? SessionResponse.AUTHENTICATED : SessionResponse.PASSKEY_ENROLLMENT);
    }

    private static void bindChallenge(HttpServletRequest request, Optional<Long> challengeId) {
        HttpSession session = request.getSession();
        // A new request always replaces the previous challenge, even when it was rate limited
        challengeId.ifPresentOrElse(id -> session.setAttribute(CHALLENGE_ATTRIBUTE, id),
                () -> session.removeAttribute(CHALLENGE_ATTRIBUTE));
    }
}
