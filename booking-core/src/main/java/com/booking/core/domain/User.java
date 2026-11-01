package com.booking.core.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MembershipTier membershipTier = MembershipTier.STANDARD;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public enum MembershipTier {
        STANDARD, PREMIUM, VIP
    }
}
