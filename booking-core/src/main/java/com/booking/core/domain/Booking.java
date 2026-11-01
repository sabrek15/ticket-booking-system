package com.booking.core.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_tier_id", nullable = false)
    private TicketTier ticketTier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status;

    @Column(nullable = false)
    private Integer seatCount;

    @Column(nullable = false)
    private Instant reservedUntil; // Grace period timestamp (e.g., now + 10 mins)

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public enum BookingStatus {
        PENDING_PAYMENT,
        CONFIRMED,
        EXPIRED,
        CANCELLED
    }
}
