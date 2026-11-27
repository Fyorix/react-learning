package fr.epita.platform.apps.ticketing.repository;

import fr.epita.platform.apps.ticketing.entity.TicketOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketOrderRepository extends JpaRepository<TicketOrder, UUID> {

    List<TicketOrder> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from TicketOrder o where o.id = :id")
    Optional<TicketOrder> lock(@Param("id") UUID id);
}
