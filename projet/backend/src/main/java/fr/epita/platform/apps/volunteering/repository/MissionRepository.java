package fr.epita.platform.apps.volunteering.repository;

import fr.epita.platform.apps.volunteering.entity.Mission;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MissionRepository extends JpaRepository<Mission, UUID> {

    List<Mission> findByMissionDateBetweenOrderByMissionDateAsc(LocalDate from, LocalDate to);

    List<Mission> findByIdIn(Collection<UUID> ids);

    /** Verrou de la mission : sérialise inscriptions et validations (places). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Mission m where m.id = :id")
    Optional<Mission> lock(@Param("id") UUID id);
}
