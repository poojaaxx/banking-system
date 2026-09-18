package com.bankingdemo.security;

import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * NOTE: on startup you'll see a benign log line from Spring Security core
 * ("Found 2 UserDetailsService beans... Global Authentication Manager will
 * not use a UserDetailsService"). That comes from
 * InitializeUserDetailsBeanManagerConfigurer building its own unused
 * fallback global AuthenticationManager and is expected here -- we never use
 * that fallback; customerAuthenticationManager/adminAuthenticationManager
 * below are the only AuthenticationManagers this app actually uses.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Marked @Primary only so Spring Security's own internal auto-config
     * (which expects a single default AuthenticationManager bean) doesn't
     * throw on startup -- our controllers always inject the specific named
     * bean they need (customerAuthenticationManager / adminAuthenticationManager)
     * and never rely on this default.
     */
    @Bean
    @Primary
    public AuthenticationManager customerAuthenticationManager(
            CustomerUserDetailsService customerUserDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(customerUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public AuthenticationManager adminAuthenticationManager(
            AdminUserDetailsService adminUserDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(adminUserDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Tracks which HTTP sessions belong to which principal so a password
     * change can invalidate the customer's other active sessions. Requires
     * HttpSessionEventPublisher below to clean up on natural session
     * expiry/logout.
     */
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                                     SecurityContextRepository securityContextRepository,
                                                     SessionRegistry sessionRegistry) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(requestHandler)
                )
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId())
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(JsonAuthEntryPoints.unauthorized(objectMapper))
                        .accessDeniedHandler(JsonAuthEntryPoints.forbidden(objectMapper))
                )
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(withDefaults -> {})
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .permissionsPolicy(policy -> policy.policy("geolocation=(), camera=(), microphone=()"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/session").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/auth/customer/register",
                                "/api/auth/customer/login",
                                "/api/auth/customer/recovery/login",
                                "/api/auth/admin/login").permitAll()
                        .requestMatchers("/api/admin/**", "/api/auth/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/customer/**", "/api/auth/customer/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/events/stream").authenticated()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/**").permitAll()
                        .anyRequest().denyAll()
                );

        return http.build();
    }
}
