package com.bankingdemo.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Manually replicates what Spring Security's filter-based form login does,
 * for our JSON login endpoints: rotate the session id (fixation protection)
 * *after* successful authentication, persist the new SecurityContext so
 * later requests on this session are recognized as authenticated, and
 * register the session so it can later be found and invalidated (e.g. on
 * password change) via {@link SessionRegistry}.
 */
@Component
@RequiredArgsConstructor
public class SessionAuthenticator {

    private final SecurityContextRepository securityContextRepository;
    private final SessionRegistry sessionRegistry;

    public void establishSession(Authentication authResult, HttpServletRequest request, HttpServletResponse response) {
        request.getSession(true);
        request.changeSessionId();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        sessionRegistry.registerNewSession(request.getSession().getId(), authResult.getPrincipal());
    }

    /** Invalidates the current session and clears the security context, regardless of which role was logged in. */
    public void endSession(HttpServletRequest request, HttpServletResponse response) {
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }

    /**
     * Expires every OTHER active session for this principal (identified by
     * equals()/hashCode() on customer/admin id), used after a password
     * change. The current session is left alone so the user isn't logged out
     * of the tab they just used to change their password.
     */
    public void invalidateOtherSessionsFor(Object principalKey, String currentSessionId) {
        for (SessionInformation info : sessionRegistry.getAllSessions(principalKey, false)) {
            if (!info.getSessionId().equals(currentSessionId)) {
                info.expireNow();
            }
        }
    }
}
