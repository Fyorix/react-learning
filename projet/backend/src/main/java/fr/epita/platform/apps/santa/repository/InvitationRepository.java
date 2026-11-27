package fr.epita.platform.apps.santa.repository;

import fr.epita.platform.apps.santa.entity.Invitation;
import fr.epita.platform.apps.santa.entity.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    List<Invitation> findByEventId(UUID eventId);

    List<Invitation> findByEventIdAndStatus(UUID eventId, InvitationStatus status);

    List<Invitation> findByUserId(UUID userId);

    Optional<Invitation> findByEventIdAndUserId(UUID eventId, UUID userId);
}
