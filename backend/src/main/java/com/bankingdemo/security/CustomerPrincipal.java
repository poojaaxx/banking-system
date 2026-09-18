package com.bankingdemo.security;

import com.bankingdemo.customer.Customer;
import com.bankingdemo.customer.CustomerStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * The only principal type ever produced by customer authentication. Ownership
 * checks throughout the service layer must go through {@link SecurityUtils}
 * and require exactly this type, never a bare numeric id read off an
 * ambiguous "current user" -- so a customer id can never be confused with an
 * admin id even if the two numeric ids happen to collide.
 */
public class CustomerPrincipal implements UserDetails {

    private final Long customerId;
    private final String username;
    private final String passwordHash;
    private final boolean active;

    public CustomerPrincipal(Customer customer) {
        this.customerId = customer.getId();
        this.username = customer.getUsername();
        this.passwordHash = customer.getPasswordHash();
        this.active = customer.getStatus() == CustomerStatus.ACTIVE;
    }

    public Long getCustomerId() {
        return customerId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    /**
     * Equality is based on identity alone (not credentials/state) so that
     * {@link org.springframework.security.core.session.SessionRegistry} can
     * find every active session for this customer across separate logins,
     * each of which constructs a fresh CustomerPrincipal instance.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CustomerPrincipal other)) return false;
        return customerId.equals(other.customerId);
    }

    @Override
    public int hashCode() {
        return customerId.hashCode();
    }
}
