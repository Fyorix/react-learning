package fr.epita.platform.apps.attendance.repository;

import fr.epita.platform.apps.attendance.entity.AttendanceSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {

    List<AttendanceSession> findByTeacherIdAndStartAtBetweenOrderByStartAtAsc(UUID teacherId, Instant from, Instant to);

    List<AttendanceSession> findByTeacherIdOrderByStartAtDesc(UUID teacherId);

    List<AttendanceSession> findByIdIn(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AttendanceSession s where s.id = :id")
    Optional<AttendanceSession> lock(@Param("id") UUID id);
}
