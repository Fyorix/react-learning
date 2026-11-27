package fr.epita.platform.apps.parking.repository;

import fr.epita.platform.apps.parking.entity.SpotRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SpotReleaseRepository extends JpaRepository<SpotRelease, SpotRelease.Key> {

    List<SpotRelease> findByIdDay(LocalDate day);

    List<SpotRelease> findByIdSpotIdAndIdDayGreaterThanEqualOrderByIdDayAsc(UUID spotId, LocalDate from);

    List<SpotRelease> findByIdDayBetween(LocalDate from, LocalDate to);
}
