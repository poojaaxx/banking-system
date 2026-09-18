package com.bankingdemo.adminapi.dto;

import java.util.List;

public record AdminCustomerDetail(AdminCustomerSummary customer, List<AdminAccountSummary> accounts) {
}
