package com.bankingdemo.beneficiary;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    List<Beneficiary> findByCustomerIdOrderByNicknameAsc(Long customerId);

    Optional<Beneficiary> findByCustomerIdAndId(Long customerId, Long id);

    boolean existsByCustomerIdAndBeneficiaryAccountId(Long customerId, Long beneficiaryAccountId);
}
