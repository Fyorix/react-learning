package fr.epita.platform.apps.timetable.repository;

import fr.epita.platform.apps.timetable.entity.TimetableGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimetableGroupRepository extends JpaRepository<TimetableGroup, UUID> {

    List<TimetableGroup> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    @Query("select g from TimetableGroup g join g.studentIds s where s = :u")
    Optional<TimetableGroup> ofStudent(@Param("u") UUID studentId);
}
