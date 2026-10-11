package com.crossborderpayment.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RemitterModelTests {

    // --- e-mail address normalization ---

    @Test
    void normalizerTrimsAndLowerCases() {
        assertThat(EmailAddressNormalizer.normalize("  Taro@Example.COM ")).isEqualTo("taro@example.com");
    }

    @Test
    void normalizerKeepsNull() {
        assertThat(EmailAddressNormalizer.normalize(null)).isNull();
    }

    @Test
    void normalizerDoesNotDependOnDefaultLocale() {
        // "I" must become "i" even under the Turkish locale (which would produce a dotless i)
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            assertThat(EmailAddressNormalizer.normalize("INFO@EXAMPLE.COM")).isEqualTo("info@example.com");
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    @Test
    void constructorAndSetterNormalizeEmailAddress() {
        RemitterModel created = new RemitterModel(null, "Taro", "Yamada", " Taro@Example.com ");
        RemitterModel updated = new RemitterModel();
        updated.setEmailAddr("HANAKO@Example.com");

        assertThat(created.getEmailAddr()).isEqualTo("taro@example.com");
        assertThat(updated.getEmailAddr()).isEqualTo("hanako@example.com");
    }

    // --- equals / hashCode ---

    @Test
    void entitiesWithSameIdAreEqual() {
        RemitterModel a = new RemitterModel(1L, "Taro", "Yamada", "taro@example.com");
        RemitterModel b = new RemitterModel(1L, "Changed", "Name", "other@example.com");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void entitiesWithDifferentIdsAreNotEqual() {
        assertThat(new RemitterModel(1L, "Taro", "Yamada", "taro@example.com"))
                .isNotEqualTo(new RemitterModel(2L, "Taro", "Yamada", "taro@example.com"));
    }

    @Test
    void unsavedEntitiesAreOnlyEqualToThemselves() {
        RemitterModel a = new RemitterModel(null, "Taro", "Yamada", "taro@example.com");
        RemitterModel b = new RemitterModel(null, "Taro", "Yamada", "taro@example.com");

        assertThat(a).isEqualTo(a);
        assertThat(a).isNotEqualTo(b);
        assertThat(a).isNotEqualTo(null);
    }
}
