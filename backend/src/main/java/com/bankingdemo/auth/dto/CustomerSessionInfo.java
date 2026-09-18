package com.bankingdemo.auth.dto;

import com.bankingdemo.customer.Customer;

public record CustomerSessionInfo(Long id, String username, String fullName, String email) {
    public static CustomerSessionInfo from(Customer c) {
        return new CustomerSessionInfo(c.getId(), c.getUsername(), c.getFullName(), c.getEmail());
    }
}
