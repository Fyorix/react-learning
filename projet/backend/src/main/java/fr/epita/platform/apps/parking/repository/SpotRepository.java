package fr.epita.platform.apps.parking.repository;

import fr.epita.platform.apps.parking.entity.Spot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpotRepository extends JpaRepository<Spot, UUID> {

    List<Spot> findAllByOrderByNumberAsc();

    Optional<Spot> findByHolderId(UUID holderId);

    boolean existsByNumberIgnoreCase(String number);

    /** Verrou de toutes les places : sérialise les réservations d'un même jour. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Spot s order by s.number")
    List<Spot> lockAll();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Spot s where s.id = :id")
    Optional<Spot> lock(@Param("id") UUID id);
}
