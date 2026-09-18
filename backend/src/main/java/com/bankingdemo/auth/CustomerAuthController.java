package com.bankingdemo.auth;

import com.bankingdemo.alert.AlertEvaluationService;
import com.bankingdemo.auth.dto.*;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.customer.Customer;
import com.bankingdemo.customer.CustomerRepository;
import com.bankingdemo.customer.CustomerService;
import com.bankingdemo.config.AppProperties;
import com.bankingdemo.recovery.RecoveryService;
import com.bankingdemo.security.ClientIpResolver;
import com.bankingdemo.security.CustomerPrincipal;
import com.bankingdemo.security.RateLimiter;
import com.bankingdemo.security.SecurityUtils;
import com.bankingdemo.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/auth/customer")
public class CustomerAuthController {

    private final CustomerService customerService;
    private final CustomerRepository customerRepository;
    private final RecoveryService recoveryService;
    private final AuthenticationManager customerAuthenticationManager;
    private final SessionAuthenticator sessionAuthenticator;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final AppProperties appProperties;
    private final PasswordEncoder passwordEncoder;
    private final AlertEvaluationService alertEvaluationService;

    // Explicit constructor (not Lombok's @RequiredArgsConstructor) so the
    // @Qualifier below is honored regardless of which AuthenticationManager
    // bean is marked @Primary -- see the matching note in AdminAuthController.
    public CustomerAuthController(CustomerService customerService,
                                   CustomerRepository customerRepository,
                                   RecoveryService recoveryService,
                                   @Qualifier("customerAuthenticationManager") AuthenticationManager customerAuthenticationManager,
                                   SessionAuthenticator sessionAuthenticator,
                                   RateLimiter rateLimiter,
                                   ClientIpResolver clientIpResolver,
                                   AppProperties appProperties,
                                   PasswordEncoder passwordEncoder,
                                   AlertEvaluationService alertEvaluationService) {
        this.customerService = customerService;
        this.customerRepository = customerRepository;
        this.recoveryService = recoveryService;
        this.customerAuthenticationManager = customerAuthenticationManager;
        this.sessionAuthenticator = sessionAuthenticator;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.appProperties = appProperties;
        this.passwordEncoder = passwordEncoder;
        this.alertEvaluationService = alertEvaluationService;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                       HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        enforceRateLimit("register", httpRequest, appProperties.getRateLimit().getRegisterPerHour(), Duration.ofHours(1));

        Customer customer = customerService.register(request.fullName(), request.email(), request.username(), request.password());
        List<String> recoveryCodes = recoveryService.regenerate(customer.getId());

        CustomerPrincipal principal = new CustomerPrincipal(customer);
        Authentication authResult = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        sessionAuthenticator.establishSession(authResult, httpRequest, httpResponse);

        return ResponseEntity.ok(new RegisterResponse(CustomerSessionInfo.from(customer), recoveryCodes));
    }

    @PostMapping("/login")
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request,
                                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        enforceRateLimit("login", httpRequest, appProperties.getRateLimit().getLoginPerMinute(), Duration.ofMinutes(1));

        Authentication authResult;
        try {
            authResult = customerAuthenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            Long customerId = customerRepository.findByUsername(request.username()).map(Customer::getId).orElse(null);
            alertEvaluationService.recordFailedLogin(request.username(), customerId);
            throw ex;
        }
        sessionAuthenticator.establishSession(authResult, httpRequest, httpResponse);

        Customer customer = customerRepository.findById(((CustomerPrincipal) authResult.getPrincipal()).getCustomerId())
                .orElseThrow(() -> new IllegalStateException("Authenticated customer missing"));
        return ResponseEntity.ok(SessionResponse.forCustomer(CustomerSessionInfo.from(customer)));
    }

    @PostMapping("/recovery/login")
    public ResponseEntity<SessionResponse> recoveryLogin(@Valid @RequestBody RecoveryLoginRequest request,
                                                           HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        enforceRateLimit("recovery-login", httpRequest, appProperties.getRateLimit().getRecoveryPerHour(), Duration.ofHours(1));

        Customer customer = recoveryService.redeemAndLogin(request.username(), request.code());
        CustomerPrincipal principal = new CustomerPrincipal(customer);
        Authentication authResult = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        sessionAuthenticator.establishSession(authResult, httpRequest, httpResponse);

        return ResponseEntity.ok(SessionResponse.forCustomer(CustomerSessionInfo.from(customer)));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                                HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        Long customerId = SecurityUtils.requireCustomerId();
        Customer customer = customerService.changePassword(customerId, request.currentPassword(), request.newPassword());

        String currentSessionId = httpRequest.getSession(true).getId();
        sessionAuthenticator.invalidateOtherSessionsFor(new CustomerPrincipal(customer), currentSessionId);
        return ResponseEntity.noContent().build();
    }

    /** Requires the current password (reauthentication) before issuing a new batch of codes. */
    @PostMapping("/recovery/regenerate")
    public ResponseEntity<List<String>> regenerateRecoveryCodes(@Valid @RequestBody ReauthRequest request) {
        Long customerId = SecurityUtils.requireCustomerId();
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalStateException("Authenticated customer missing"));
        if (!passwordEncoder.matches(request.password(), customer.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        return ResponseEntity.ok(recoveryService.regenerate(customerId));
    }

    @GetMapping("/recovery/status")
    public ResponseEntity<RecoveryStatus> recoveryStatus() {
        Long customerId = SecurityUtils.requireCustomerId();
        return ResponseEntity.ok(new RecoveryStatus(recoveryService.countActive(customerId)));
    }

    public record ReauthRequest(@NotBlank String password) {
    }

    public record RecoveryStatus(long activeCodeCount) {
    }

    private void enforceRateLimit(String action, HttpServletRequest request, int maxRequests, Duration window) {
        String ip = clientIpResolver.resolve(request);
        if (!rateLimiter.tryConsume(action + ":" + ip, maxRequests, window)) {
            throw ApiException.tooManyRequests("Too many attempts. Please wait and try again.");
        }
    }
}
