package com.bankingdemo.security;

import com.bankingdemo.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Only trusts X-Forwarded-For when the app is explicitly configured to sit
 * behind a trusted reverse proxy (TRUST_PROXY_HEADERS=true). Otherwise a
 * client could trivially spoof the header to defeat per-IP rate limiting.
 */
@Component
@RequiredArgsConstructor
public class ClientIpResolver {

    private final AppProperties appProperties;

    public String resolve(HttpServletRequest request) {
        if (appProperties.isTrustProxyHeaders()) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (forwardedFor != null && !forwardedFor.isBlank()) {
                return forwardedFor.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
