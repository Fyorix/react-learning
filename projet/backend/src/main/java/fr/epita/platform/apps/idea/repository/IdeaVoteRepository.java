package fr.epita.platform.apps.idea.repository;

import fr.epita.platform.apps.idea.entity.IdeaVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface IdeaVoteRepository extends JpaRepository<IdeaVote, IdeaVote.Key> {

    List<IdeaVote> findByIdUserIdAndIdIdeaIdIn(UUID userId, Collection<UUID> ideaIds);
}
