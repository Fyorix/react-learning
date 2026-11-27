package fr.epita.platform.apps.carpool.repository;

import fr.epita.platform.apps.carpool.entity.Trip;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    List<Trip> findByDepartureAtAfterOrderByDepartureAtAsc(Instant after);

    List<Trip> findByDriverIdOrderByDepartureAtDesc(UUID driverId);

    List<Trip> findByIdInOrderByDepartureAtDesc(Collection<UUID> ids);

    List<Trip> findByDepartureAtBetweenOrderByDepartureAtAsc(Instant from, Instant to);

    /** Verrou sur le trajet : sérialise les réservations concurrentes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t where t.id = :id")
    Optional<Trip> lockById(UUID id);
}
