package fr.epita.platform.apps.grade.repository;

import fr.epita.platform.apps.grade.entity.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    List<Assessment> findBySubjectIdInAndPeriodIdOrderByDateAsc(Collection<UUID> subjectIds, UUID periodId);

    List<Assessment> findBySubjectIdOrderByDateAsc(UUID subjectId);
}
