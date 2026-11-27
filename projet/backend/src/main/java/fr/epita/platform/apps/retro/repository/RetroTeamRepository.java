package fr.epita.platform.apps.retro.repository;

import fr.epita.platform.apps.retro.entity.RetroTeam;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface RetroTeamRepository extends JpaRepository<RetroTeam, UUID> {

    @Query("select t from RetroTeam t join t.memberIds m where m = :u order by t.name")
    List<RetroTeam> ofMember(@Param("u") UUID userId);
}
