package com.booking.core.service;


import com.booking.core.domain.User;

public interface UserService {
    User registerUser(String fullName, String email, String rawPassword);
    User getUserByEmail(String email);
    boolean existsByEmail(String email);
}
