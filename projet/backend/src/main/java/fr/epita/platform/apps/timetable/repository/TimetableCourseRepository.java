package fr.epita.platform.apps.timetable.repository;

import fr.epita.platform.apps.timetable.entity.TimetableCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TimetableCourseRepository extends JpaRepository<TimetableCourse, UUID> {

    /** Cours qui chevauchent l'intervalle [start, end[. */
    @Query("select c from TimetableCourse c where c.startAt < :end and c.endAt > :start")
    List<TimetableCourse> overlapping(@Param("start") Instant start, @Param("end") Instant end);

    List<TimetableCourse> findBySubjectId(UUID subjectId);
}
