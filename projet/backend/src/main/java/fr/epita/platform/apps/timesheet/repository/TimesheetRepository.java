package fr.epita.platform.apps.timesheet.repository;

import fr.epita.platform.apps.timesheet.entity.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimesheetRepository extends JpaRepository<Timesheet, UUID> {

    Optional<Timesheet> findByUserIdAndWeek(UUID userId, String week);

    List<Timesheet> findByUserId(UUID userId);

    List<Timesheet> findByUserIdInOrderByWeekDesc(Collection<UUID> userIds);

    List<Timesheet> findAllByOrderByWeekDesc();
}
