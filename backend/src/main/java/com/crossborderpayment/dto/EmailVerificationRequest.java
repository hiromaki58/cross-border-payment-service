package com.crossborderpayment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EmailVerificationRequest(@NotBlank @Pattern(regexp = "^[0-9]{8}$") String code) {
}
