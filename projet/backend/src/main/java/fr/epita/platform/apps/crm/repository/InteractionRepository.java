package fr.epita.platform.apps.crm.repository;

import fr.epita.platform.apps.crm.entity.Interaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InteractionRepository extends JpaRepository<Interaction, UUID> {

    List<Interaction> findByAccountIdOrderByDateDescCreatedAtDesc(UUID accountId);

    List<Interaction> findByOpportunityIdOrderByDateDescCreatedAtDesc(UUID opportunityId);
}
