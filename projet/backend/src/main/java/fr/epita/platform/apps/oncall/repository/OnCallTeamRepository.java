package fr.epita.platform.apps.oncall.repository;

import fr.epita.platform.apps.oncall.entity.OnCallTeam;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OnCallTeamRepository extends JpaRepository<OnCallTeam, UUID> {

    List<OnCallTeam> findAllByOrderByNameAsc();

    @Query("select distinct t from OnCallTeam t left join t.memberIds m where t.managerId = :u or m = :u order by t.name")
    List<OnCallTeam> involving(@Param("u") UUID userId);

    boolean existsByNameIgnoreCase(String name);

    /** Verrou de l'équipe : sérialise génération, affectations et échanges. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OnCallTeam t where t.id = :id")
    Optional<OnCallTeam> lock(@Param("id") UUID id);
}
