package com.booking.api.security;

import com.booking.core.config.datasource.RoutingContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReadAfterWriteFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                String userEmail = auth.getName();
                String key = "write:recent:user:" + userEmail;

                Boolean hasRecentWrite = redisTemplate.hasKey(key);

                if (Boolean.TRUE.equals(hasRecentWrite)) {
                    log.info("⚡ [Read-After-Write Filter] Active write flag detected for user {}! Overriding to PRIMARY.", userEmail);
                    RoutingContextHolder.setForcePrimary(true);
                }
            }

            filterChain.doFilter(request, response);
        } finally {
           RoutingContextHolder.clear();
        }
    }
}
