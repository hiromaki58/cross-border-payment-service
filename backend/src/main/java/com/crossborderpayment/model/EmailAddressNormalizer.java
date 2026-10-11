package com.crossborderpayment.model;

import java.util.Locale;

/**
 * Normalizes e-mail addresses so that the same address is always stored and searched in one form
 * (e.g. " Taro@Example.COM " -> "taro@example.com").
 * Must match the database check constraint: email_addr = LOWER(TRIM(email_addr)).
 */
public final class EmailAddressNormalizer {

    private EmailAddressNormalizer(){
    }

    public static String normalize(String emailAddr){
        if (emailAddr == null) {
            return null;
        }
        // Locale.ROOT avoids locale-dependent conversion (e.g. Turkish dotless i)
        return emailAddr.trim().toLowerCase(Locale.ROOT);
    }
}
