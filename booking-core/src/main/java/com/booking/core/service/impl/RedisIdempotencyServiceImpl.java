package com.booking.core.service.impl;

import com.booking.core.service.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisIdempotencyServiceImpl implements IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyServiceImpl.class);
    private static final String KEY_PREFIX = "idempotency:";

    private final StringRedisTemplate redisTemplate;

    public RedisIdempotencyServiceImpl(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean acquireLock(String idempotencyKey, Duration timeout) {
        String redisKey = KEY_PREFIX + idempotencyKey;

        Boolean isSuccess = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PROCESSING", timeout);

        log.debug("Idempotency lock attempt for key {}: {}", redisKey, isSuccess);
        return Boolean.TRUE.equals(isSuccess);
    }

    @Override
    public void releaseLock(String idempotencyLock) {
        String redisKey = KEY_PREFIX + idempotencyLock;
        redisTemplate.delete(redisKey);
    }
}
