package fr.epita.platform.apps.access.repository;

import fr.epita.platform.apps.access.entity.AccessRequest;
import fr.epita.platform.apps.access.entity.AccessRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {

    List<AccessRequest> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<AccessRequest> findByStatusInOrderByCreatedAtAsc(Collection<AccessRequestStatus> statuses);

    List<AccessRequest> findByUserIdAndStatusIn(UUID userId, Collection<AccessRequestStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AccessRequest r where r.id = :id")
    Optional<AccessRequest> lock(@Param("id") UUID id);
}
