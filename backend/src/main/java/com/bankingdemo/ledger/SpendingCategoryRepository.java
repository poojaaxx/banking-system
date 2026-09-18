package com.bankingdemo.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpendingCategoryRepository extends JpaRepository<SpendingCategory, Long> {
    Optional<SpendingCategory> findByCode(String code);
}
