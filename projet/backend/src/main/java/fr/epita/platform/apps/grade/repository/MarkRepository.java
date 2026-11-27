package fr.epita.platform.apps.grade.repository;

import fr.epita.platform.apps.grade.entity.Mark;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MarkRepository extends JpaRepository<Mark, Mark.Key> {

    List<Mark> findByIdAssessmentIdIn(Collection<UUID> assessmentIds);
}
