package fr.epita.platform.apps.jargon.repository;

import fr.epita.platform.apps.jargon.entity.ContributionStatus;
import fr.epita.platform.apps.jargon.entity.JargonDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JargonDefinitionRepository extends JpaRepository<JargonDefinition, UUID> {

    List<JargonDefinition> findByTermId(UUID termId);

    List<JargonDefinition> findByStatusOrderByCreatedAtAsc(ContributionStatus status);

    List<JargonDefinition> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);
}
