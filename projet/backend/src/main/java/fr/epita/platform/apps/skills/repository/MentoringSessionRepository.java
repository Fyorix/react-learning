package fr.epita.platform.apps.skills.repository;

import fr.epita.platform.apps.skills.entity.MentoringSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MentoringSessionRepository extends JpaRepository<MentoringSession, UUID> {

    List<MentoringSession> findByPairIdOrderByDateDesc(UUID pairId);
}
