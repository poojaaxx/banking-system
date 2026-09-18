package com.bankingdemo.budget;

import java.math.BigDecimal;

public record BudgetProgress(Long categoryId, String categoryName, BigDecimal limitAmount, BigDecimal spentAmount) {
}
