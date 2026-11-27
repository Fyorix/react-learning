package fr.epita.platform.apps.budget.repository;

import fr.epita.platform.apps.budget.entity.PurchaseRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, UUID> {

    List<PurchaseRequest> findByTeamIdAndYear(UUID teamId, int year);

    List<PurchaseRequest> findByTeamIdOrderByCreatedAtDesc(UUID teamId);
}
