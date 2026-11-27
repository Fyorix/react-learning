package fr.epita.platform.apps.retro.repository;

import fr.epita.platform.apps.retro.entity.Retro;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RetroRepository extends JpaRepository<Retro, UUID> {

    List<Retro> findByTeamIdInOrderByCreatedAtDesc(Collection<UUID> teamIds);

    /** Verrou de la rétrospective : sérialise phases, votes et regroupements. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Retro r where r.id = :id")
    Optional<Retro> lock(@Param("id") UUID id);
}
