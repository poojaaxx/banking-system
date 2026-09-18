package com.bankingdemo.savingsgoal.dto;

import com.bankingdemo.account.Account;
import com.bankingdemo.savingsgoal.SavingsGoal;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingsGoalResponse(
        Long id, String name, BigDecimal targetAmount, LocalDate targetDate,
        Long linkedAccountId, String linkedAccountNumber, BigDecimal currentBalance, String status
) {
    public static SavingsGoalResponse from(SavingsGoal g, Account account) {
        return new SavingsGoalResponse(g.getId(), g.getName(), g.getTargetAmount(), g.getTargetDate(),
                g.getLinkedAccountId(), account.getAccountNumber(), account.getBalance(), g.getStatus().name());
    }
}
