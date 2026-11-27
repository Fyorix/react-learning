package fr.epita.platform.apps.enps.repository;

import fr.epita.platform.apps.enps.entity.SurveyResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SurveyResponseRepository extends JpaRepository<SurveyResponse, UUID> {

    List<SurveyResponse> findBySurveyId(UUID surveyId);
}
