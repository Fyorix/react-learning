package fr.epita.platform.apps.volunteering.repository;

import fr.epita.platform.apps.volunteering.entity.VolunteerRegistration;
import fr.epita.platform.apps.volunteering.entity.VolunteerStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VolunteerRegistrationRepository extends JpaRepository<VolunteerRegistration, UUID> {

    List<VolunteerRegistration> findByMissionId(UUID missionId);

    Optional<VolunteerRegistration> findByMissionIdAndUserId(UUID missionId, UUID userId);

    List<VolunteerRegistration> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<VolunteerRegistration> findByUserIdInAndStatus(Collection<UUID> userIds, VolunteerStatus status);

    List<VolunteerRegistration> findByStatus(VolunteerStatus status);

    long countByMissionIdAndStatusIn(UUID missionId, Collection<VolunteerStatus> statuses);
}
