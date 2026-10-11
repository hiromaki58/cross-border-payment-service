package com.crossborderpayment.model;

public enum EmailVerificationPurpose {
    // New remitter: verify the address, create the remitter, then register the first passkey
    REGISTRATION,
    // Existing remitter who lost all passkeys: verify the address, then register a new passkey
    RECOVERY
}
