package fr.epita.platform.apps.supply.repository;

import fr.epita.platform.apps.supply.entity.RequestStatus;
import fr.epita.platform.apps.supply.entity.SupplyRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplyRequestRepository extends JpaRepository<SupplyRequest, UUID> {

    List<SupplyRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<SupplyRequest> findAllByOrderByCreatedAtAsc();

    List<SupplyRequest> findByStatusOrderByCreatedAtAsc(RequestStatus status);

    List<SupplyRequest> findByUserIdAndStatusInAndCreatedAtBetween(UUID userId, Collection<RequestStatus> statuses, Instant from, Instant to);

    List<SupplyRequest> findByStatusAndDeliveredAtBetween(RequestStatus status, Instant from, Instant to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SupplyRequest r where r.id = :id")
    Optional<SupplyRequest> lock(@Param("id") UUID id);
}
