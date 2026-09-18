package com.bankingdemo.billpay.dto;

import com.bankingdemo.billpay.Biller;

public record BillerResponse(Long id, String code, String name) {
    public static BillerResponse from(Biller b) {
        return new BillerResponse(b.getId(), b.getCode(), b.getName());
    }
}
