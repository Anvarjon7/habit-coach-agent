package com.example.habit_coach_agent.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

/** Server-to-server REST operations; Telegram ingress has its own authentication. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class InternalApiKeyFilter extends OncePerRequestFilter {
    public static final String HEADER = "X-Internal-Api-Key";
    private final byte[] expected;

    public InternalApiKeyFilter(@Value("${coach.internal-api-key:}") String apiKey) {
        expected = apiKey == null || apiKey.isBlank() ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = applicationPath(request);
        // Keep only the intended public health surfaces, even if more actuator endpoints
        // are accidentally exposed by environment configuration in a future deployment.
        if (isUnder(path, "/actuator") && !publicHealth(path, request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (isUnder(path, "/api") && !authorized(request)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getWriter().write("{\"error\":\"Unauthorized\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean authorized(HttpServletRequest request) {
        var values = Collections.list(request.getHeaders(HEADER));
        if (expected.length == 0 || values.size() != 1) return false;
        String supplied = values.getFirst();
        return supplied != null && supplied.length() <= 1024
                && MessageDigest.isEqual(expected, supplied.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean publicHealth(String path, String method) {
        return ("GET".equals(method) || "HEAD".equals(method))
                && (path.equals("/actuator/health") || path.equals("/actuator/health/liveness")
                || path.equals("/actuator/health/readiness"));
    }

    private static boolean isUnder(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static String applicationPath(HttpServletRequest request) {
        // The container's servlet path is decoded and excludes the context path.
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI().substring(request.getContextPath().length());
        }
        // Matrix parameters do not change Spring's route identity.
        return path.replaceAll(";[^/]*", "").replaceAll("/{2,}", "/");
    }
}
