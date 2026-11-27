package fr.epita.platform.apps.tournament.repository;

import fr.epita.platform.apps.tournament.entity.Entry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EntryRepository extends JpaRepository<Entry, UUID> {

    List<Entry> findByTournamentIdOrderByCreatedAtAsc(UUID tournamentId);

    long countByTournamentId(UUID tournamentId);

    @Query("select e from Entry e where e.player1Id = :u or e.player2Id = :u")
    List<Entry> ofPlayer(@Param("u") UUID userId);
}
