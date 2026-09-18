package com.bankingdemo.notification;

import com.bankingdemo.security.AdminPrincipal;
import com.bankingdemo.security.CustomerPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public record CurrentRecipient(RecipientType type, Long id) {

    public static CurrentRecipient resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomerPrincipal customerPrincipal) {
            return new CurrentRecipient(RecipientType.CUSTOMER, customerPrincipal.getCustomerId());
        }
        if (auth != null && auth.getPrincipal() instanceof AdminPrincipal adminPrincipal) {
            return new CurrentRecipient(RecipientType.ADMIN, adminPrincipal.getAdminId());
        }
        throw new AccessDeniedException("Authentication required");
    }
}
