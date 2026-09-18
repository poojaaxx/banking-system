package com.bankingdemo.auth.dto;

import com.bankingdemo.admin.Admin;

public record AdminSessionInfo(Long id, String username) {
    public static AdminSessionInfo from(Admin a) {
        return new AdminSessionInfo(a.getId(), a.getUsername());
    }
}
