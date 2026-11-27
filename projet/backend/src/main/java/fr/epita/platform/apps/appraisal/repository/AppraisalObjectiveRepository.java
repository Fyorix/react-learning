package fr.epita.platform.apps.appraisal.repository;

import fr.epita.platform.apps.appraisal.entity.AppraisalObjective;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppraisalObjectiveRepository extends JpaRepository<AppraisalObjective, UUID> {

    List<AppraisalObjective> findByAppraisalIdOrderByPositionAsc(UUID appraisalId);
}
