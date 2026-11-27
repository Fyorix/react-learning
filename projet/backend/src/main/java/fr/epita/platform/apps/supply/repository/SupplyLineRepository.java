package fr.epita.platform.apps.supply.repository;

import fr.epita.platform.apps.supply.entity.SupplyLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SupplyLineRepository extends JpaRepository<SupplyLine, UUID> {

    List<SupplyLine> findByRequestIdOrderByCreatedAtAsc(UUID requestId);

    List<SupplyLine> findByRequestIdIn(Collection<UUID> requestIds);
}
