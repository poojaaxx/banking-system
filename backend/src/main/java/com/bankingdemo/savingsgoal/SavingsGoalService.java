package com.bankingdemo.savingsgoal;

import com.bankingdemo.account.Account;
import com.bankingdemo.account.AccountService;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.ledger.LedgerService;
import com.bankingdemo.ledger.MoneyMovementReceipt;
import com.bankingdemo.ledger.SpendingCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Each goal is backed by its own dedicated savings account -- progress is
 * always that account's real ledger balance, contributed to via the normal
 * idempotent transfer path (no separate ledger mechanism, no double-counting).
 */
@Service
@RequiredArgsConstructor
public class SavingsGoalService {

    private final SavingsGoalRepository savingsGoalRepository;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final SpendingCategoryRepository spendingCategoryRepository;

    @Transactional
    public SavingsGoal create(Long customerId, String name, BigDecimal targetAmount, LocalDate targetDate) {
        if (targetAmount == null || targetAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Target amount must be positive");
        }
        Account account = accountService.create(customerId, "Goal: " + name);

        SavingsGoal goal = new SavingsGoal();
        goal.setCustomerId(customerId);
        goal.setName(name);
        goal.setTargetAmount(targetAmount);
        goal.setTargetDate(targetDate);
        goal.setLinkedAccountId(account.getId());
        goal.setStatus(GoalStatus.ACTIVE);
        return savingsGoalRepository.save(goal);
    }

    @Transactional(readOnly = true)
    public List<SavingsGoal> list(Long customerId) {
        return savingsGoalRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
    }

    @Transactional(readOnly = true)
    public SavingsGoal getOwned(Long customerId, Long goalId) {
        return savingsGoalRepository.findByCustomerIdAndId(customerId, goalId)
                .orElseThrow(() -> ApiException.notFound("Savings goal not found"));
    }

    public MoneyMovementReceipt contribute(Long customerId, Long goalId, Long sourceAccountId, BigDecimal amount, String idempotencyKey) {
        SavingsGoal goal = getOwned(customerId, goalId);
        if (goal.getStatus() != GoalStatus.ACTIVE) {
            throw ApiException.badRequest("This goal is no longer active");
        }
        Account goalAccount = ledgerService.requireOwnedActiveAccount(customerId, goal.getLinkedAccountId());
        Long savingsCategoryId = spendingCategoryRepository.findByCode("SAVINGS").map(c -> c.getId()).orElse(null);

        MoneyMovementReceipt receipt = ledgerService.transfer(customerId, sourceAccountId, goalAccount.getAccountNumber(),
                amount, "Savings goal: " + goal.getName(), savingsCategoryId, idempotencyKey);

        markCompletedIfTargetReached(goal);
        return receipt;
    }

    @Transactional
    public void close(Long customerId, Long goalId) {
        SavingsGoal goal = getOwned(customerId, goalId);
        goal.setStatus(GoalStatus.CLOSED);
        savingsGoalRepository.save(goal);
    }

    @Transactional
    void markCompletedIfTargetReached(SavingsGoal goal) {
        Account account = ledgerService.requireOwnedActiveAccount(goal.getCustomerId(), goal.getLinkedAccountId());
        if (goal.getStatus() == GoalStatus.ACTIVE && account.getBalance().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(GoalStatus.COMPLETED);
            savingsGoalRepository.save(goal);
        }
    }
}
