package com.bankingdemo.beneficiary.dto;

import com.bankingdemo.account.Account;
import com.bankingdemo.beneficiary.Beneficiary;

public record BeneficiaryResponse(Long id, String accountNumber, String nickname) {
    public static BeneficiaryResponse from(Beneficiary b, Account account) {
        return new BeneficiaryResponse(b.getId(), account.getAccountNumber(), b.getNickname());
    }
}
