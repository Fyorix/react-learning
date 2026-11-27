package fr.epita.platform.apps.license.repository;

import fr.epita.platform.apps.license.entity.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    List<Seat> findBySoftwareIdOrderByCreatedAtAsc(UUID softwareId);

    List<Seat> findByUserId(UUID userId);

    Optional<Seat> findBySoftwareIdAndUserId(UUID softwareId, UUID userId);

    long countBySoftwareId(UUID softwareId);
}
