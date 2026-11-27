package fr.epita.platform.apps.association.repository;

import fr.epita.platform.apps.association.entity.AssociationRegistration;
import fr.epita.platform.apps.association.entity.AssociationRegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssociationRegistrationRepository extends JpaRepository<AssociationRegistration, UUID> {

    List<AssociationRegistration> findByActivityIdOrderByCreatedAtAscIdAsc(UUID activityId);

    long countByActivityIdAndStatus(UUID activityId, AssociationRegistrationStatus status);

    Optional<AssociationRegistration> findByActivityIdAndUserId(UUID activityId, UUID userId);
}
