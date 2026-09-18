package com.bankingdemo.budget.dto;

import com.bankingdemo.ledger.SpendingCategory;

public record SpendingCategoryResponse(Long id, String code, String name) {
    public static SpendingCategoryResponse from(SpendingCategory c) {
        return new SpendingCategoryResponse(c.getId(), c.getCode(), c.getName());
    }
}
