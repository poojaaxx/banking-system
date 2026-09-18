package com.bankingdemo.security;

import com.bankingdemo.admin.Admin;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class AdminPrincipal implements UserDetails {

    private final Long adminId;
    private final String username;
    private final String passwordHash;

    public AdminPrincipal(Admin admin) {
        this.adminId = admin.getId();
        this.username = admin.getUsername();
        this.passwordHash = admin.getPasswordHash();
    }

    public Long getAdminId() {
        return adminId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
