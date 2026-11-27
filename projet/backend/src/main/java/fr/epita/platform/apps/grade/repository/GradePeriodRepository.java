package fr.epita.platform.apps.grade.repository;

import fr.epita.platform.apps.grade.entity.GradePeriod;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradePeriodRepository extends JpaRepository<GradePeriod, UUID> {

    List<GradePeriod> findAllByOrderByStartDateAsc();

    boolean existsByNameIgnoreCase(String name);

    /** Verrou partagé : une saisie et un verrouillage simultanés sont sérialisés. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from GradePeriod p where p.id = :id")
    Optional<GradePeriod> share(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from GradePeriod p where p.id = :id")
    Optional<GradePeriod> lock(@Param("id") UUID id);
}
