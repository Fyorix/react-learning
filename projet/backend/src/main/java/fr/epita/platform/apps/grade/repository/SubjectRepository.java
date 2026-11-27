package fr.epita.platform.apps.grade.repository;

import fr.epita.platform.apps.grade.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    List<Subject> findByClassIdOrderByNameAsc(UUID classId);

    List<Subject> findByTeacherIdOrderByNameAsc(UUID teacherId);

    boolean existsByClassIdAndNameIgnoreCase(UUID classId, String name);

    boolean existsByClassIdAndTeacherId(UUID classId, UUID teacherId);
}
