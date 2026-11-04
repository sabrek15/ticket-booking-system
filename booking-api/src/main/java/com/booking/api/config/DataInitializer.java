package com.booking.api.config;

import com.booking.core.domain.Event;
import com.booking.core.domain.TicketTier;
import com.booking.core.domain.User;
import com.booking.core.repository.EventRepository;
import com.booking.core.repository.TicketTierRepository;
import com.booking.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Configuration
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final TicketTierRepository ticketTierRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           EventRepository eventRepository,
                           TicketTierRepository ticketTierRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.ticketTierRepository = ticketTierRepository;
        this.eventRepository = eventRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            log.info("Seeding initial database test data...");

            // 1. Create a Test User
            User user = new User();
            user.setFullName("John Doe");
            user.setEmail("john.doe@example.com");
            user.setPassword(passwordEncoder.encode("password123"));
            user.setMembershipTier(User.MembershipTier.VIP);
            User savedUser = userRepository.save(user);

            // 2. Create a Test Event
            Event event = new Event();
            event.setName("Coldplay Music of the Spheres");
            event.setDescription("Live concert in Mumbai");
            event.setVenue("DY Patil Stadium");
            event.setStartTime(Instant.now().plus(30, ChronoUnit.DAYS));
            event.setEndTime(Instant.now().plus(30, ChronoUnit.DAYS).plus(4, ChronoUnit.HOURS));
            event.setStatus(Event.EventStatus.PUBLISHED);
            Event savedEvent = eventRepository.save(event);

            // 3. Create a Ticket Tier with only 5 SEATS
            TicketTier tier = new TicketTier();
            tier.setEvent(savedEvent);
            tier.setName("VIP Lounge");
            tier.setPrice(new BigDecimal("250.00"));
            tier.setTotalCapacity(5);
            tier.setAvailableCapacity(5); // Only 5 available!
            TicketTier savedTier = ticketTierRepository.save(tier);

            log.info("Seeding complete! User ID: {}, Event ID: {}, TicketTier ID: {}",
                    savedUser.getId(), savedEvent.getId(), savedTier.getId());
        }
    }
}