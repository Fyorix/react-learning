package fr.epita.platform.apps.recruitment.repository;

import fr.epita.platform.apps.recruitment.entity.StageEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StageEventRepository extends JpaRepository<StageEvent, UUID> {

    List<StageEvent> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
