package fr.epita.platform.apps.fitness.repository;

import fr.epita.platform.apps.fitness.entity.FitnessClass;
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

public interface FitnessClassRepository extends JpaRepository<FitnessClass, UUID> {

    List<FitnessClass> findByStartsAtBetweenOrderByStartsAtAsc(Instant from, Instant to);

    List<FitnessClass> findByCoachIdAndStartsAtAfterOrderByStartsAtAsc(UUID coachId, Instant after);

    List<FitnessClass> findByIdIn(Collection<UUID> ids);

    /** Verrou du cours : sérialise réservations, annulations et promotions depuis la liste d'attente. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FitnessClass c where c.id = :id")
    Optional<FitnessClass> lock(@Param("id") UUID id);
}
