package fr.epita.platform.apps.grade.repository;

import fr.epita.platform.apps.grade.entity.GradeComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GradeCommentRepository extends JpaRepository<GradeComment, UUID> {

    List<GradeComment> findByPeriodIdAndStudentId(UUID periodId, UUID studentId);

    List<GradeComment> findByPeriodIdAndSubjectId(UUID periodId, UUID subjectId);
}
