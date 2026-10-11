package com.crossborderpayment.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Enforces the absolute session lifetime: once it has passed, the session is invalidated and
 * the request continues as unauthenticated (protected endpoints then return 401).
 * The idle timeout is handled by the servlet container (server.servlet.session.timeout).
 */
public class SessionLifetimeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(SessionLifetimeFilter.class);

    private final AuthSessionManager authSessionManager;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
            .getContextHolderStrategy();

    public SessionLifetimeFilter(AuthSessionManager authSessionManager) {
        this.authSessionManager = authSessionManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && authSessionManager.isExpired(session)) {
            session.invalidate();
            securityContextHolderStrategy.setContext(securityContextHolderStrategy.createEmptyContext());
            log.info("Authenticated session reached its absolute lifetime and was invalidated");
        }
        filterChain.doFilter(request, response);
    }
}
