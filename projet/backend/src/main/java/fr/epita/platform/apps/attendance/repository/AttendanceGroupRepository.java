package fr.epita.platform.apps.attendance.repository;

import fr.epita.platform.apps.attendance.entity.AttendanceGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceGroupRepository extends JpaRepository<AttendanceGroup, UUID> {

    List<AttendanceGroup> findAllByOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    @Query("select g from AttendanceGroup g join g.studentIds s where s = :u")
    Optional<AttendanceGroup> ofStudent(@Param("u") UUID studentId);
}
