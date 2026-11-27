package fr.epita.platform.apps.ticketing.repository;

import fr.epita.platform.apps.ticketing.entity.TicketRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRateRepository extends JpaRepository<TicketRate, UUID> {

    List<TicketRate> findByEventIdOrderByPositionAsc(UUID eventId);
}
