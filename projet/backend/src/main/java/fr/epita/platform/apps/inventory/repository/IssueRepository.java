package fr.epita.platform.apps.inventory.repository;

import fr.epita.platform.apps.inventory.entity.Issue;
import fr.epita.platform.apps.inventory.entity.IssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IssueRepository extends JpaRepository<Issue, UUID> {

    List<Issue> findByEquipmentIdOrderByCreatedAtDesc(UUID equipmentId);

    List<Issue> findByEquipmentIdAndStatus(UUID equipmentId, IssueStatus status);

    List<Issue> findByStatusOrderByCreatedAtAsc(IssueStatus status);

    List<Issue> findAllByOrderByCreatedAtDesc();
}
