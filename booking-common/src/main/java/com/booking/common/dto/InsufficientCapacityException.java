package com.booking.common.dto;

public class InsufficientCapacityException extends RuntimeException {
    public InsufficientCapacityException(String message) {
        super(message);
    }
}
