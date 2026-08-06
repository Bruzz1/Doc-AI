package com.bruce.docai.security.filter;

import com.bruce.docai.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class MustChangePasswordFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!(authentication.getPrincipal() instanceof User user) || user.getMustChangePassword() != Boolean.TRUE) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        if (isAllowedDuringPasswordReset(uri)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean expectsHtml = request.getHeader(HttpHeaders.ACCEPT) != null
                && request.getHeader(HttpHeaders.ACCEPT).contains("text/html");

        if (expectsHtml) {
            response.sendRedirect("/change-password");
        } else {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"password_change_required\"}");
        }
    }

    private boolean isAllowedDuringPasswordReset(String uri) {
        return uri.startsWith("/auth/me")
                || uri.startsWith("/auth/logout")
                || uri.startsWith("/auth/change-password")
                || uri.startsWith("/auth/refresh")
                || uri.startsWith("/change-password")
                || uri.startsWith("/style.css")
                || uri.startsWith("/favicon.ico");
    }
}

