package fr.epita.platform.apps.enps.repository;

import fr.epita.platform.apps.enps.entity.Participation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParticipationRepository extends JpaRepository<Participation, Participation.Key> {

    List<Participation> findByIdSurveyId(UUID surveyId);

    long countByIdSurveyId(UUID surveyId);
}
