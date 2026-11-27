package fr.epita.platform.apps.crm.repository;

import fr.epita.platform.apps.crm.entity.FollowUpTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FollowUpTaskRepository extends JpaRepository<FollowUpTask, UUID> {

    List<FollowUpTask> findByOwnerIdOrderByDueDateAsc(UUID ownerId);
}
