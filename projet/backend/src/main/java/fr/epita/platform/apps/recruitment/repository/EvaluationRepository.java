package fr.epita.platform.apps.recruitment.repository;

import fr.epita.platform.apps.recruitment.entity.Evaluation;
import fr.epita.platform.apps.recruitment.entity.Recommendation;
import fr.epita.platform.apps.recruitment.entity.Stage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvaluationRepository extends JpaRepository<Evaluation, UUID> {

    List<Evaluation> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    boolean existsByApplicationIdAndEvaluatorIdAndStage(UUID applicationId, UUID evaluatorId, Stage stage);

    long countByApplicationIdAndRecommendation(UUID applicationId, Recommendation recommendation);
}
