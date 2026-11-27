package fr.epita.platform.apps.enps.repository;

import fr.epita.platform.apps.enps.entity.SurveyAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, UUID> {

    List<SurveyAnswer> findByResponseIdIn(Collection<UUID> responseIds);
}
