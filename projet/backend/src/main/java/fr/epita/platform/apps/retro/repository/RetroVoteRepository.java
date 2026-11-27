package fr.epita.platform.apps.retro.repository;

import fr.epita.platform.apps.retro.entity.RetroVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RetroVoteRepository extends JpaRepository<RetroVote, RetroVote.Key> {

    List<RetroVote> findByIdRetroId(UUID retroId);

    List<RetroVote> findByIdRetroIdAndIdUserId(UUID retroId, UUID userId);
}
