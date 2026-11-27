package fr.epita.platform.apps.attendance.repository;

import fr.epita.platform.apps.attendance.entity.AttendanceEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceEntryRepository extends JpaRepository<AttendanceEntry, UUID> {

    List<AttendanceEntry> findBySessionId(UUID sessionId);

    List<AttendanceEntry> findByStudentId(UUID studentId);

    Optional<AttendanceEntry> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);
}
