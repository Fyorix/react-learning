package fr.epita.platform.apps.access.repository;

import fr.epita.platform.apps.access.entity.AccessGrant;
import fr.epita.platform.apps.access.entity.GrantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID> {

    Optional<AccessGrant> findByToolIdAndUserIdAndStatus(UUID toolId, UUID userId, GrantStatus status);

    List<AccessGrant> findByUserIdAndStatus(UUID userId, GrantStatus status);

    List<AccessGrant> findByToolIdAndStatusOrderByExpiresAtAsc(UUID toolId, GrantStatus status);

    List<AccessGrant> findByStatus(GrantStatus status);
}
