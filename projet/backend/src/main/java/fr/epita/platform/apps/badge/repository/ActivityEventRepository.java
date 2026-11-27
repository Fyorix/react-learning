package fr.epita.platform.apps.badge.repository;

import fr.epita.platform.apps.badge.entity.ActivityEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

    List<ActivityEvent> findByUserIdAndType(UUID userId, String type);

    long countByUserIdAndType(UUID userId, String type);
}
