package fr.epita.platform.apps.retro.repository;

import fr.epita.platform.apps.retro.entity.RetroAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RetroActionRepository extends JpaRepository<RetroAction, UUID> {

    List<RetroAction> findByRetroIdOrderByCreatedAtAsc(UUID retroId);

    List<RetroAction> findByTeamIdAndDoneFalseOrderByDueDateAsc(UUID teamId);

    long countByRetroId(UUID retroId);
}
