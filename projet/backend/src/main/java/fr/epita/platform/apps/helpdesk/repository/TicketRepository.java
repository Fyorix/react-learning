package fr.epita.platform.apps.helpdesk.repository;

import fr.epita.platform.apps.helpdesk.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findAllByOrderByCreatedAtDesc();

    List<Ticket> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);
}
