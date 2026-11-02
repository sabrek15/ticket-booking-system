package com.booking.core.service;

import java.time.Duration;

public interface IdempotencyService {
    boolean acquireLock(String idempotencyKey, Duration timeout);
    void releaseLock(String idempotencyKey);
}
