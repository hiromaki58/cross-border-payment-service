package com.crossborderpayment.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.crossborderpayment.model.EmailVerificationChallengeModel;

import jakarta.persistence.LockModeType;

@Repository
public interface EmailVerificationChallengeRepository extends JpaRepository<EmailVerificationChallengeModel, Long> {

    // Row lock so that concurrent attempts are counted correctly and a code can be used only once
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM EmailVerificationChallengeModel c WHERE c.id = :id")
    Optional<EmailVerificationChallengeModel> findByIdForUpdate(@Param("id") Long id);

    // Callers pass a normalized address (see EmailAddressNormalizer)
    long countByEmailAddrAndCreatedAtAfter(String emailAddr, Instant after);

    @Modifying
    @Query("DELETE FROM EmailVerificationChallengeModel c WHERE c.createdAt < :before")
    int deleteCreatedBefore(@Param("before") Instant before);
}
