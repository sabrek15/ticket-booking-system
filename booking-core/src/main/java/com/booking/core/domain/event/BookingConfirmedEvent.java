package com.booking.core.domain.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

@Getter
public class BookingConfirmedEvent extends ApplicationEvent {

    private final Long bookingId;
    private final String customerEmail;
    private final String customerName;
    private final String eventName;
    private final String tierName;
    private final int seatCount;
    private final BigDecimal totalPrice;

    public BookingConfirmedEvent(
            Object source,
            Long bookingId,
            String customerEmail,
            String customerName,
            String eventName,
            String tierName,
            int seatCount,
            BigDecimal totalPrice
    ) {
        super(source);
        this.bookingId = bookingId;
        this.customerEmail = customerEmail;
        this.customerName = customerName;
        this.eventName = eventName;
        this.tierName = tierName;
        this.seatCount = seatCount;
        this.totalPrice = totalPrice;
    }
}
