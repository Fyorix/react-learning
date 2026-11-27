package fr.epita.platform.apps.appraisal.repository;

import fr.epita.platform.apps.appraisal.entity.AppraisalAnswer;
import fr.epita.platform.apps.appraisal.entity.Author;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppraisalAnswerRepository extends JpaRepository<AppraisalAnswer, UUID> {

    List<AppraisalAnswer> findByAppraisalIdAndAuthor(UUID appraisalId, Author author);
}
