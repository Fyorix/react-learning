package fr.epita.platform.apps.attendance.repository;

import fr.epita.platform.apps.attendance.entity.Justification;
import fr.epita.platform.apps.attendance.entity.JustificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JustificationRepository extends JpaRepository<Justification, UUID> {

    List<Justification> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<Justification> findByStatusOrderByCreatedAtAsc(JustificationStatus status);

    List<Justification> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Justification j where j.id = :id")
    Optional<Justification> lock(@Param("id") UUID id);
}
