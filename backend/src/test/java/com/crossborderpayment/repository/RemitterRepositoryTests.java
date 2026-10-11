package com.crossborderpayment.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.hibernate.proxy.HibernateProxy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.crossborderpayment.model.RemitterModel;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

/**
 * Runs against the schema created by the Flyway migrations (Hibernate only validates it).
 */
@DataJpaTest
class RemitterRepositoryTests {

    @Autowired
    private RemitterRepository remitterRepository;

    @Autowired
    private EntityManager entityManager;

    private static RemitterModel remitter(String emailAddr) {
        return new RemitterModel(null, "Taro", "Yamada", emailAddr);
    }

    @Test
    void savesAndFindsRemitterByEmailAddress() {
        remitterRepository.saveAndFlush(remitter("taro@example.com"));

        assertThat(remitterRepository.findByEmail("taro@example.com"))
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

        assertThat(remitterRepository.existsByEmail("taro@example.com")).isTrue();
        assertThat(remitterRepository.existsByEmail("other@example.com")).isFalse();
        assertThat(remitterRepository.findByEmail("other@example.com")).isEmpty();
    }

    @Test
    void storesTheTableAsRemitters() {
        remitterRepository.saveAndFlush(remitter("taro@example.com"));

        Number count = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM remitters").getSingleResult();
        assertThat(count.intValue()).isEqualTo(1);
    }

    // --- e-mail address normalization ---

    @Test
    void storesEmailAddressInNormalizedForm() {
        remitterRepository.saveAndFlush(remitter("  Taro@Example.COM "));

        String stored = (String) entityManager
                .createNativeQuery("SELECT email_addr FROM remitters")
                .getSingleResult();
        assertThat(stored).isEqualTo("taro@example.com");
    }

    @Test
    void findsRemitterRegardlessOfCaseAndSurroundingSpaces() {
        remitterRepository.saveAndFlush(remitter("taro@example.com"));

        assertThat(remitterRepository.findByEmail(" TARO@Example.com  ")).isPresent();
        assertThat(remitterRepository.existsByEmail("Taro@EXAMPLE.COM")).isTrue();
    }

    @Test
    void sameAddressWithDifferentCaseIsRejectedAsDuplicate() {
        remitterRepository.saveAndFlush(remitter("taro@example.com"));

        assertThatThrownBy(() -> remitterRepository.saveAndFlush(remitter(" TARO@Example.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNonNormalizedEmailAddressWrittenDirectly() {
        // Even if the application normalization is bypassed, the check constraint keeps the data consistent
        assertThatThrownBy(() -> entityManager
                .createNativeQuery("INSERT INTO remitters (first_name, family_name, email_addr) VALUES ('Taro', 'Yamada', 'Taro@Example.com')")
                .executeUpdate())
                .isInstanceOf(PersistenceException.class);
    }

    // --- required data and query safety ---

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

        assertThat(remitterRepository.findByEmail("' OR '1'='1")).isEmpty();
        assertThat(remitterRepository.existsByEmail("' OR '1'='1")).isFalse();
    }

    // --- equals / hashCode with Hibernate proxies ---

    @Test
    void proxyAndLoadedEntityWithSameIdAreEqual() {
        Long id = remitterRepository.saveAndFlush(remitter("taro@example.com")).getId();
        entityManager.clear();

        RemitterModel proxy = remitterRepository.getReferenceById(id);
        assertThat(proxy).isInstanceOf(HibernateProxy.class);
        entityManager.clear();
        RemitterModel loaded = remitterRepository.findById(id).orElseThrow();
        assertThat(loaded).isNotInstanceOf(HibernateProxy.class);

        assertThat(proxy).isEqualTo(loaded);
        assertThat(loaded).isEqualTo(proxy);
        assertThat(proxy.hashCode()).isEqualTo(loaded.hashCode());
    }

    @Test
    void entityStaysInHashSetAfterIdIsAssignedOnSave() {
        RemitterModel remitter = remitter("taro@example.com");
        Set<RemitterModel> set = new HashSet<>();
        set.add(remitter);

        remitterRepository.saveAndFlush(remitter);

        assertThat(remitter.getId()).isNotNull();
        assertThat(set).contains(remitter);
    }
}
