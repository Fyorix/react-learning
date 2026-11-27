package fr.epita.platform.apps.enps.repository;

import fr.epita.platform.apps.enps.entity.SurveyQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, UUID> {

    List<SurveyQuestion> findBySurveyIdOrderByPositionAsc(UUID surveyId);
}
