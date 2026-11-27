package fr.epita.platform.apps.timetable.repository;

import fr.epita.platform.apps.timetable.entity.TimetableSubject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TimetableSubjectRepository extends JpaRepository<TimetableSubject, UUID> {

    List<TimetableSubject> findAllByOrderByNameAsc();

    List<TimetableSubject> findByGroupIdOrderByNameAsc(UUID groupId);

    boolean existsByGroupIdAndNameIgnoreCase(UUID groupId, String name);
}
