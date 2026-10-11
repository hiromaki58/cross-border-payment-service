package com.crossborderpayment.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.crossborderpayment.dto.PasskeyResponse;
import com.crossborderpayment.dto.ValidationPatterns;
import com.crossborderpayment.security.AuthSessionManager;
import com.crossborderpayment.security.ProblemResponses;
import com.crossborderpayment.service.PasskeyService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Pattern;

/**
 * Passkeys of the logged-in remitter (requires ROLE_REMITTER, see SecurityConfig).
 * New passkeys are added with /api/auth/passkey/register/options and /api/auth/passkey/register.
 */
@RestController
@RequestMapping("/api/passkeys")
public class PasskeyController {

    private final PasskeyService passkeyService;
    private final AuthSessionManager authSessionManager;

    public PasskeyController(PasskeyService passkeyService, AuthSessionManager authSessionManager) {
        this.passkeyService = passkeyService;
        this.authSessionManager = authSessionManager;
    }

    @GetMapping
    public List<PasskeyResponse> list(Authentication authentication) {
        return passkeyService.list(authentication.getName());
    }

    @DeleteMapping("/{credentialId}")
    public ResponseEntity<?> delete(@PathVariable @Pattern(regexp = ValidationPatterns.CREDENTIAL_ID) String credentialId,
            Authentication authentication, HttpServletRequest request) {
        // Removing an authenticator is a sensitive change: require a recent passkey login
        if (!authSessionManager.isRecentlyAuthenticated(request)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ProblemResponses.reauthenticationRequired());
        }
        return switch (passkeyService.delete(authentication.getName(), credentialId)) {
            case DELETED -> ResponseEntity.noContent().build();
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The passkey was not found."));
            case LAST_PASSKEY -> ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "The last passkey cannot be removed."));
        };
    }
}
