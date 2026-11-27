package fr.epita.platform.apps.oncall.repository;

import fr.epita.platform.apps.oncall.entity.Swap;
import fr.epita.platform.apps.oncall.entity.SwapStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SwapRepository extends JpaRepository<Swap, UUID> {

    List<Swap> findByRequesterIdOrderByCreatedAtDesc(UUID requesterId);

    List<Swap> findByOtherUserIdOrderByCreatedAtDesc(UUID otherUserId);

    List<Swap> findByTeamIdInAndStatusOrderByCreatedAtAsc(Collection<UUID> teamIds, SwapStatus status);
}
