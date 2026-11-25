package cn.innersight.common.web;

import java.io.IOException;
import java.util.Set;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class ApiBoundary extends OncePerRequestFilter {
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");
    private final String origin;

    ApiBoundary(@Value("${innersight.origin}") String origin) { this.origin = origin; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (!SAFE.contains(request.getMethod()) && !origin.equals(request.getHeader("Origin"))) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"ORIGIN_REJECTED\",\"message\":\"请求来源未获允许。\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
