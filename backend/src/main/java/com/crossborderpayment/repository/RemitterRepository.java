package com.crossborderpayment.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.crossborderpayment.model.RemitterModel;

@Repository
public interface RemitterRepository extends JpaRepository<RemitterModel, Long>{
    Optional<RemitterModel> findByEmailAddr(String emailAddr);
    boolean existsByEmailAddr(String emailAddr);
}
