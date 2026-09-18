package com.bankingdemo.auth;

import com.bankingdemo.admin.Admin;
import com.bankingdemo.admin.AdminRepository;
import com.bankingdemo.auth.dto.AdminSessionInfo;
import com.bankingdemo.auth.dto.LoginRequest;
import com.bankingdemo.auth.dto.SessionResponse;
import com.bankingdemo.common.ApiException;
import com.bankingdemo.config.AppProperties;
import com.bankingdemo.security.AdminPrincipal;
import com.bankingdemo.security.ClientIpResolver;
import com.bankingdemo.security.RateLimiter;
import com.bankingdemo.security.SessionAuthenticator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth/admin")
public class AdminAuthController {

    private final AdminRepository adminRepository;
    private final AuthenticationManager adminAuthenticationManager;
    private final SessionAuthenticator sessionAuthenticator;
    private final RateLimiter rateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final AppProperties appProperties;

    // Explicit constructor (not Lombok's @RequiredArgsConstructor) so the
    // @Qualifier below is honored -- customerAuthenticationManager is marked
    // @Primary (only to satisfy Spring Security's own internal lookup of a
    // single default AuthenticationManager), and @Primary silently wins over
    // parameter-name-based autowiring, which is what this @Qualifier guards
    // against. See CLAUDE.md / SecurityConfig for the full explanation.
    public AdminAuthController(AdminRepository adminRepository,
                                @Qualifier("adminAuthenticationManager") AuthenticationManager adminAuthenticationManager,
                                SessionAuthenticator sessionAuthenticator,
                                RateLimiter rateLimiter,
                                ClientIpResolver clientIpResolver,
                                AppProperties appProperties) {
        this.adminRepository = adminRepository;
        this.adminAuthenticationManager = adminAuthenticationManager;
        this.sessionAuthenticator = sessionAuthenticator;
        this.rateLimiter = rateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.appProperties = appProperties;
    }

    @PostMapping("/login")
    public ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request,
                                                  HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String ip = clientIpResolver.resolve(httpRequest);
        if (!rateLimiter.tryConsume("admin-login:" + ip, appProperties.getRateLimit().getLoginPerMinute(), Duration.ofMinutes(1))) {
            throw ApiException.tooManyRequests("Too many attempts. Please wait and try again.");
        }

        Authentication authResult = adminAuthenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        sessionAuthenticator.establishSession(authResult, httpRequest, httpResponse);

        Admin admin = adminRepository.findById(((AdminPrincipal) authResult.getPrincipal()).getAdminId())
                .orElseThrow(() -> new IllegalStateException("Authenticated admin missing"));
        return ResponseEntity.ok(SessionResponse.forAdmin(AdminSessionInfo.from(admin)));
    }
}
