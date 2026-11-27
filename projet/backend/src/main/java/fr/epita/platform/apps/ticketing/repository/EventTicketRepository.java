package fr.epita.platform.apps.ticketing.repository;

import fr.epita.platform.apps.ticketing.entity.EventTicket;
import fr.epita.platform.apps.ticketing.entity.EventTicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventTicketRepository extends JpaRepository<EventTicket, UUID> {

    List<EventTicket> findByOrderId(UUID orderId);

    List<EventTicket> findByEventIdAndStatus(UUID eventId, EventTicketStatus status);

    long countByRateIdAndStatus(UUID rateId, EventTicketStatus status);

    long countByEventIdAndStatus(UUID eventId, EventTicketStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from EventTicket t where t.code = :code")
    Optional<EventTicket> lockByCode(@Param("code") String code);
}
