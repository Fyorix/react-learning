package fr.epita.platform.apps.enps.repository;

import fr.epita.platform.apps.enps.entity.Survey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SurveyRepository extends JpaRepository<Survey, UUID> {

    List<Survey> findAllByOrderByOpensAtDesc();

    List<Survey> findAllByOrderByOpensAtAsc();
}
