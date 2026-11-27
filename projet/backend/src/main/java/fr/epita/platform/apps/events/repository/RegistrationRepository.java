package fr.epita.platform.apps.events.repository;

import fr.epita.platform.apps.events.entity.Registration;
import fr.epita.platform.apps.events.entity.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationRepository extends JpaRepository<Registration, UUID> {

    List<Registration> findByEventIdOrderByCreatedAtAsc(UUID eventId);

    List<Registration> findByEventIdAndStatusOrderByCreatedAtAsc(UUID eventId, RegistrationStatus status);

    long countByEventIdAndStatus(UUID eventId, RegistrationStatus status);

    Optional<Registration> findByEventIdAndUserId(UUID eventId, UUID userId);

    List<Registration> findByUserId(UUID userId);

    List<Registration> findByEventIdIn(Collection<UUID> eventIds);
}
