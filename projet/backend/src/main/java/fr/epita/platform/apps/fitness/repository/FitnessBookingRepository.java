package fr.epita.platform.apps.fitness.repository;

import fr.epita.platform.apps.fitness.entity.FitnessBookingStatus;
import fr.epita.platform.apps.fitness.entity.FitnessBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FitnessBookingRepository extends JpaRepository<FitnessBooking, UUID> {

    List<FitnessBooking> findByClassIdOrderByCreatedAtAsc(UUID classId);

    Optional<FitnessBooking> findByClassIdAndUserId(UUID classId, UUID userId);

    List<FitnessBooking> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<FitnessBooking> findByUserIdAndStatusIn(UUID userId, Collection<FitnessBookingStatus> statuses);

    List<FitnessBooking> findByUserIdAndDebitedTrue(UUID userId);

    long countByClassIdAndStatus(UUID classId, FitnessBookingStatus status);

    List<FitnessBooking> findByClassIdIn(Collection<UUID> classIds);
}
