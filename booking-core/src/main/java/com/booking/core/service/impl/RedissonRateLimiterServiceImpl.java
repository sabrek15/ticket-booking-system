package com.booking.core.service.impl;

import com.booking.core.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedissonRateLimiterServiceImpl implements RateLimiterService {

    private final RedissonClient redissonClient;

    private static final long RATE = 5;
    private static final long RATE_INTERVAL = 1;
    private static final RateIntervalUnit RATE_UNIT = RateIntervalUnit.MINUTES;

    @Override
    public boolean tryAcquire(String key) {
        String rateLimitKey = "rate_limit:" + key;
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(rateLimitKey);

        rateLimiter.trySetRate(RateType.OVERALL, RATE, RATE_INTERVAL, RATE_UNIT);

        rateLimiter.expire(Duration.ofHours(2));

        boolean acquired = rateLimiter.tryAcquire(1);
        if (!acquired) {
            log.warn("⚠️ Rate limit EXCEEDED for key: {}", key);
        }

        return acquired;
    }
}
