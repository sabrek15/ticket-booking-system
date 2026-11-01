package com.booking.core.repository;

import com.booking.core.domain.TicketTier;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TicketTierRepository extends JpaRepository<TicketTier, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
            @QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000")
    })
    @Query("SELECT t FROM TicketTier t WHERE t.id = :id")
    Optional<TicketTier> findByIdWithPessimisticLock(@Param("id") Long id);

    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE ticket_tiers
        SET available_capacity = available_capacity - :requestedSeats
        WHERE id = :id AND available_capacity >= :requestedSeats
    """, nativeQuery = true)
    int reserveSeatsDirectly(@Param("id") Long id, @Param("requestedSeats") int requestedSeats);
}
