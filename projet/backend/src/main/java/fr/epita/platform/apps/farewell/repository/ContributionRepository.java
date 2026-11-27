package fr.epita.platform.apps.farewell.repository;

import fr.epita.platform.apps.farewell.entity.Contribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContributionRepository extends JpaRepository<Contribution, UUID> {

    List<Contribution> findByPotIdOrderByCreatedAtAsc(UUID potId);

    Optional<Contribution> findByPotIdAndUserId(UUID potId, UUID userId);

    boolean existsByPotId(UUID potId);
}
