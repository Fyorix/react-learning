package fr.epita.platform.apps.goals.repository;

import fr.epita.platform.apps.goals.entity.GoalProgress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GoalProgressRepository extends JpaRepository<GoalProgress, UUID> {

    List<GoalProgress> findByGoalIdOrderByCreatedAtAsc(UUID goalId);

    boolean existsByGoalId(UUID goalId);
}
