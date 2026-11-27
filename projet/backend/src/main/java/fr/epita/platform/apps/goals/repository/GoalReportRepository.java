package fr.epita.platform.apps.goals.repository;

import fr.epita.platform.apps.goals.entity.GoalReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GoalReportRepository extends JpaRepository<GoalReport, UUID> {

    List<GoalReport> findByManagerId(UUID managerId);
}
