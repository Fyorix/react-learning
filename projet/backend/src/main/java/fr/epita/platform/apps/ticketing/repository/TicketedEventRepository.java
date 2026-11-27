package fr.epita.platform.apps.ticketing.repository;

import fr.epita.platform.apps.ticketing.entity.TicketedEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketedEventRepository extends JpaRepository<TicketedEvent, UUID> {

    List<TicketedEvent> findByStartsAtAfterOrderByStartsAtAsc(Instant now);

    List<TicketedEvent> findAllByOrderByStartsAtDesc();

    /** Verrou de l'événement : les achats simultanés sont sérialisés, la jauge n'est jamais dépassée. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from TicketedEvent e where e.id = :id")
    Optional<TicketedEvent> lock(@Param("id") UUID id);
}
