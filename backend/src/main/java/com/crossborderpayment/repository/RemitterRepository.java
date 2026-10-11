package com.crossborderpayment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.crossborderpayment.model.EmailAddressNormalizer;
import com.crossborderpayment.model.RemitterModel;

@Repository
public interface RemitterRepository extends JpaRepository<RemitterModel, Long>{
    // These derived queries compare the stored (normalized) value as-is.
    // Callers should use findByEmail / existsByEmail, which normalize the input first.
    Optional<RemitterModel> findByEmailAddr(String emailAddr);
    boolean existsByEmailAddr(String emailAddr);

    default Optional<RemitterModel> findByEmail(String emailAddr){
        return findByEmailAddr(EmailAddressNormalizer.normalize(emailAddr));
    }

    default boolean existsByEmail(String emailAddr){
        return existsByEmailAddr(EmailAddressNormalizer.normalize(emailAddr));
    }
}
