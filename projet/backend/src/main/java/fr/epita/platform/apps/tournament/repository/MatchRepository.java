package fr.epita.platform.apps.tournament.repository;

import fr.epita.platform.apps.tournament.entity.Match;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    List<Match> findByTournamentIdOrderByRoundAscPositionAsc(UUID tournamentId);

    Optional<Match> findByTournamentIdAndRoundAndPosition(UUID tournamentId, int round, int position);

    @Query("select m from Match m where m.entryAId in :entries or m.entryBId in :entries order by m.createdAt desc")
    List<Match> ofEntries(@Param("entries") Collection<UUID> entryIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :id")
    Optional<Match> lock(@Param("id") UUID id);
}
