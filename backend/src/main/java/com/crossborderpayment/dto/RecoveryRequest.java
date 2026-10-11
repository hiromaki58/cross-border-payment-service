package com.crossborderpayment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Start of account recovery (all passkeys lost): a one-time code is sent to the address.
 */
public record RecoveryRequest(@NotBlank @Email @Size(max = 254) String emailAddr) {
}
