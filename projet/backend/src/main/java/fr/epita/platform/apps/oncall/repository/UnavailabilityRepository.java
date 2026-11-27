package fr.epita.platform.apps.oncall.repository;

import fr.epita.platform.apps.oncall.entity.Unavailability;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface UnavailabilityRepository extends JpaRepository<Unavailability, UUID> {

    List<Unavailability> findByTeamIdAndWeekStartBetween(UUID teamId, LocalDate from, LocalDate to);

    boolean existsByTeamIdAndUserIdAndWeekStart(UUID teamId, UUID userId, LocalDate weekStart);
}
