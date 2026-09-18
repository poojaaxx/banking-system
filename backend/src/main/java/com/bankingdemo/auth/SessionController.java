package com.bankingdemo.auth;

import com.bankingdemo.admin.AdminRepository;
import com.bankingdemo.auth.dto.AdminSessionInfo;
import com.bankingdemo.auth.dto.CustomerSessionInfo;
import com.bankingdemo.auth.dto.SessionResponse;
import com.bankingdemo.customer.CustomerRepository;
import com.bankingdemo.notification.RecipientType;
import com.bankingdemo.notification.sse.SseEventPublisher;
import com.bankingdemo.security.AdminPrincipal;
import com.bankingdemo.security.CustomerPrincipal;
import com.bankingdemo.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Who am I" and logout, shared between the customer and admin frontends --
 * both just report/clear whatever is currently in the session, since a
 * session only ever holds exactly one of a CustomerPrincipal or AdminPrincipal.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SessionController {

    private final CustomerRepository customerRepository;
    private final AdminRepository adminRepository;
    private final SessionAuthenticator sessionAuthenticator;
    private final SseEventPublisher sseEventPublisher;

    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.ok(SessionResponse.anonymous());
        }
        if (auth.getPrincipal() instanceof CustomerPrincipal customerPrincipal) {
            return customerRepository.findById(customerPrincipal.getCustomerId())
                    .map(c -> ResponseEntity.ok(SessionResponse.forCustomer(CustomerSessionInfo.from(c))))
                    .orElseGet(() -> ResponseEntity.ok(SessionResponse.anonymous()));
        }
        if (auth.getPrincipal() instanceof AdminPrincipal adminPrincipal) {
            return adminRepository.findById(adminPrincipal.getAdminId())
                    .map(a -> ResponseEntity.ok(SessionResponse.forAdmin(AdminSessionInfo.from(a))))
                    .orElseGet(() -> ResponseEntity.ok(SessionResponse.anonymous()));
        }
        return ResponseEntity.ok(SessionResponse.anonymous());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            if (auth.getPrincipal() instanceof CustomerPrincipal customerPrincipal) {
                sseEventPublisher.closeAll(RecipientType.CUSTOMER, customerPrincipal.getCustomerId());
            } else if (auth.getPrincipal() instanceof AdminPrincipal adminPrincipal) {
                sseEventPublisher.closeAll(RecipientType.ADMIN, adminPrincipal.getAdminId());
            }
        }
        sessionAuthenticator.endSession(request, response);
        return ResponseEntity.noContent().build();
    }
}
