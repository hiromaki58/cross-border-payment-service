package com.crossborderpayment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Start of a new registration: a one-time code is sent to the address.
 */
public record RegistrationRequest(
        @NotBlank @Email @Size(max = 254) String emailAddr,
        @NotBlank @Size(max = 100) @Pattern(regexp = ValidationPatterns.NO_CONTROL_CHARACTERS) String firstName,
        @NotBlank @Size(max = 100) @Pattern(regexp = ValidationPatterns.NO_CONTROL_CHARACTERS) String familyName) {
}
