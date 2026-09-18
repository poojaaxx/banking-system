package com.bankingdemo.savingsgoal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {

    List<SavingsGoal> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Optional<SavingsGoal> findByCustomerIdAndId(Long customerId, Long id);

    Optional<SavingsGoal> findByLinkedAccountId(Long linkedAccountId);
}
