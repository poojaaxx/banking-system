package com.bankingdemo.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Single source of truth for "who is the current user". Every service method
 * that scopes data to a customer or admin must go through here rather than
 * trusting a path variable or a raw id pulled off the Authentication object,
 * so a customer can never act as an admin (or vice versa) even if their
 * numeric ids collide.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Long requireCustomerId() {
        return requireCustomerPrincipal().getCustomerId();
    }

    public static CustomerPrincipal requireCustomerPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomerPrincipal customerPrincipal) {
            return customerPrincipal;
        }
        throw new AccessDeniedException("Customer authentication required");
    }

    public static Long requireAdminId() {
        return requireAdminPrincipal().getAdminId();
    }

    public static AdminPrincipal requireAdminPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal adminPrincipal) {
            return adminPrincipal;
        }
        throw new AccessDeniedException("Admin authentication required");
    }
}
