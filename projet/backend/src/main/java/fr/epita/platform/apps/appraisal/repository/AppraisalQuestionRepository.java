package fr.epita.platform.apps.appraisal.repository;

import fr.epita.platform.apps.appraisal.entity.AppraisalQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppraisalQuestionRepository extends JpaRepository<AppraisalQuestion, UUID> {

    List<AppraisalQuestion> findByCampaignIdOrderByPositionAsc(UUID campaignId);
}
