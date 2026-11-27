package fr.epita.platform.apps.oncall.repository;

import fr.epita.platform.apps.oncall.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    List<Shift> findByTeamIdAndWeekStartBetweenOrderByWeekStartAsc(UUID teamId, LocalDate from, LocalDate to);

    Optional<Shift> findByTeamIdAndWeekStart(UUID teamId, LocalDate weekStart);

    List<Shift> findByUserIdAndWeekStartGreaterThanEqualOrderByWeekStartAsc(UUID userId, LocalDate from);

    List<Shift> findByTeamIdOrderByWeekStartAsc(UUID teamId);
}
