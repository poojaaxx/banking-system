package com.bankingdemo.adminapi.dto;

import com.bankingdemo.customer.Customer;

import java.time.Instant;

public record AdminCustomerSummary(Long id, String fullName, String email, String username, String status, Instant createdAt) {
    public static AdminCustomerSummary from(Customer c) {
        return new AdminCustomerSummary(c.getId(), c.getFullName(), c.getEmail(), c.getUsername(), c.getStatus().name(), c.getCreatedAt());
    }
}
