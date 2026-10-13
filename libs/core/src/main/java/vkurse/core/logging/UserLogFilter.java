/*
 * Copyright (C) vkurse.space - All Rights Reserved
 * Unauthorized copying of this file, via any medium is strictly prohibited
 * Proprietary and confidential
 */
package vkurse.core.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import vkurse.core.security.UserSession;

/**
 * Names the caller in the log: {@code userId} is in the MDC while an authenticated request is handled. It runs after
 * the security filters, which know the caller, and leaves the id for {@link HttpLogFilter}, which writes the request's
 * line after they have forgotten it.
 */
public class UserLogFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserSession session)) {
            chain.doFilter(request, response);
            return;
        }
        var userId = session.userId().toString();
        request.setAttribute(HttpLogFilter.USER_ID_ATTRIBUTE, userId);
        MDC.put(HttpLogFilter.USER_ID, userId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(HttpLogFilter.USER_ID);
        }
    }
}
