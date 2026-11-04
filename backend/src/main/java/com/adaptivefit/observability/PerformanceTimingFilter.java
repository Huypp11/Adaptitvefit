package com.adaptivefit.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class PerformanceTimingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    private final boolean enabled;

    public PerformanceTimingFilter(@Value("${app.performance.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/checkins")
                && !path.startsWith("/api/recommendations")
                && !path.startsWith("/api/ai");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestId = UUID.randomUUID().toString();
        String endpoint = request.getMethod() + " " + request.getRequestURI();
        response.setHeader(REQUEST_ID_HEADER, requestId);
        PerformanceTiming.beginRequest(requestId, endpoint);
        try {
            filterChain.doFilter(request, response);
        } finally {
            PerformanceTiming.endRequest(response.getStatus());
        }
    }
}
