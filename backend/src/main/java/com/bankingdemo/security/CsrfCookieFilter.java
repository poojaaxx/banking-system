package com.bankingdemo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security's CSRF token is lazily resolved (a DeferredCsrfToken); the
 * repository only actually writes the XSRF-TOKEN cookie once something reads
 * the token. Touching the request attribute here forces that read on every
 * request, so the SPA can rely on the cookie being present after any GET
 * (e.g. right after login) instead of needing a dedicated "give me a token"
 * endpoint. This is the standard pattern documented by Spring Security for
 * cookie-based CSRF with a JavaScript frontend.
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
