package com.crossborderpayment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.crossborderpayment.model.RemitterModel;

/**
 * Runs against the schema created by the Flyway migration (Hibernate only validates it).
 */
@DataJpaTest
class RemitterRepositoryTests {

    @Autowired
    private RemitterRepository remitterRepository;

    private static RemitterModel remitter(String emailAddr) {
        return new RemitterModel(null, "Taro", "Yamada", emailAddr);
    }

    @Test
    void savesAndFindsRemitterByEmailAddress() {
        remitterRepository.saveAndFlush(remitter("taro@example.com"));

        assertThat(remitterRepository.findByEmailAddr("taro@example.com"))
                .get()
                .satisfies(found -> {
                    assertThat(found.getId()).isNotNull();
                    assertThat(found.getFirstName()).isEqualTo("Taro");
                    assertThat(found.getFamilyName()).isEqualTo("Yamada");
                });
    }

    @Test
    void existsByEmailAddressReflectsStoredData() {
        remitterRepository.saveAndFlush(remitter("taro@example.com"));

        assertThat(remitterRepository.existsByEmailAddr("taro@example.com")).isTrue();
        assertThat(remitterRepository.existsByEmailAddr("other@example.com")).isFalse();
        assertThat(remitterRepository.findByEmailAddr("other@example.com")).isEmpty();
    }

    @Test
    void duplicateEmailAddressIsRejected() {
        remitterRepository.saveAndFlush(remitter("taro@example.com"));

        assertThatThrownBy(() -> remitterRepository.saveAndFlush(remitter("taro@example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void missingRequiredColumnIsRejected() {
        RemitterModel noFirstName = new RemitterModel(null, null, "Yamada", "taro@example.com");

        assertThatThrownBy(() -> remitterRepository.saveAndFlush(noFirstName))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void emailAddressValueIsNotTreatedAsSql() {
        // Derived queries use bound parameters, so SQL syntax in a value is treated as plain data
        remitterRepository.saveAndFlush(remitter("taro@example.com"));

        assertThat(remitterRepository.findByEmailAddr("' OR '1'='1")).isEmpty();
        assertThat(remitterRepository.existsByEmailAddr("' OR '1'='1")).isFalse();
    }
}
