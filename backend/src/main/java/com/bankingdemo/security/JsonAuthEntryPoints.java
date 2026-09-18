package com.bankingdemo.security;

import com.bankingdemo.common.ApiError;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * This is a JSON API, not a browser form-login flow -- unauthenticated and
 * forbidden requests get a JSON 401/403 body instead of Spring Security's
 * default redirect-to-login-page or bare-status behavior.
 */
public final class JsonAuthEntryPoints {

    private JsonAuthEntryPoints() {
    }

    public static AuthenticationEntryPoint unauthorized(ObjectMapper objectMapper) {
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) -> {
            write(response, objectMapper, 401, "Unauthorized", "Authentication required", request.getRequestURI());
        };
    }

    public static AccessDeniedHandler forbidden(ObjectMapper objectMapper) {
        return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) -> {
            write(response, objectMapper, 403, "Forbidden", "You do not have access to this resource", request.getRequestURI());
        };
    }

    private static void write(HttpServletResponse response, ObjectMapper objectMapper, int status, String error, String message, String path) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiError.of(status, error, message, path)));
    }
}
