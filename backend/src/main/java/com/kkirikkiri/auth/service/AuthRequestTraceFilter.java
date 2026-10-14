package com.kkirikkiri.auth.service;

import java.io.IOException;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE+10)
public class AuthRequestTraceFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath()+"/api/auth/");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String previous=MDC.get("authRequestId");
        String id=UUID.randomUUID().toString(); // Never echo arbitrary inbound IDs into logs.
        MDC.put("authRequestId", id);
        response.setHeader("X-Request-ID", id);
        try { chain.doFilter(request, response); }
        finally {
            if (previous == null) MDC.remove("authRequestId"); else MDC.put("authRequestId", previous);
        }
    }
}
