package com.abhay.aidocument.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    @Value("${rate.limit.max-requests}")
    private int maxRequests;

    @Value("${rate.limit.window-minutes}")
    private int windowMinutes;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();

        boolean isAiApi =
                path.startsWith("/api/chat") ||
                        path.startsWith("/api/summary");

        if (!isAiApi) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String userEmail = authentication.getName();

        String key = "rate_limit:" + userEmail;

        Long currentCount = redisTemplate.opsForValue().increment(key);

        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(windowMinutes));
        }

        if (currentCount != null && currentCount > maxRequests) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("""
                    {
                      "message": "Rate limit exceeded. Please try again later."
                    }
                    """);
            return;
        }

        filterChain.doFilter(request, response);
    }
}