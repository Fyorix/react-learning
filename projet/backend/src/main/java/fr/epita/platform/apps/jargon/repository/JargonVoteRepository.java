package fr.epita.platform.apps.jargon.repository;

import fr.epita.platform.apps.jargon.entity.JargonVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JargonVoteRepository extends JpaRepository<JargonVote, JargonVote.Key> {

    long countByIdDefinitionId(UUID definitionId);
}
