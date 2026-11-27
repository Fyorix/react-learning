package fr.epita.platform.apps.training.repository;

import fr.epita.platform.apps.training.entity.TrainingSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrainingSessionRepository extends JpaRepository<TrainingSession, UUID> {

    List<TrainingSession> findByCourseIdOrderByStartsAtAsc(UUID courseId);

    /** Verrou de la session pendant les changements de places (confirmation, liste d'attente). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TrainingSession s where s.id = :id")
    Optional<TrainingSession> lock(@Param("id") UUID id);
}
