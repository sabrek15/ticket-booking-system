package com.booking.core.service;

public interface RateLimiterService {
    boolean tryAcquire(String key);
}
