package com.bankingdemo.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByCustomerIdAndMonthStart(Long customerId, LocalDate monthStart);

    Optional<Budget> findByCustomerIdAndCategoryIdAndMonthStart(Long customerId, Long categoryId, LocalDate monthStart);
}
