package fr.epita.platform.apps.carpool.repository;

import fr.epita.platform.apps.carpool.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    long countByTripId(UUID tripId);

    List<Booking> findByTripIdOrderByCreatedAtAsc(UUID tripId);

    List<Booking> findByTripIdIn(Collection<UUID> tripIds);

    List<Booking> findByPassengerId(UUID passengerId);

    Optional<Booking> findByTripIdAndPassengerId(UUID tripId, UUID passengerId);
}
