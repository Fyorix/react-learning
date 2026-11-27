package fr.epita.platform.apps.parking.repository;

import fr.epita.platform.apps.parking.entity.SpotBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpotBookingRepository extends JpaRepository<SpotBooking, UUID> {

    List<SpotBooking> findByDay(LocalDate day);

    Optional<SpotBooking> findBySpotIdAndDay(UUID spotId, LocalDate day);

    Optional<SpotBooking> findByUserIdAndDay(UUID userId, LocalDate day);

    List<SpotBooking> findByUserIdAndDayGreaterThanEqualOrderByDayAsc(UUID userId, LocalDate from);

    List<SpotBooking> findByDayBetween(LocalDate from, LocalDate to);
}
