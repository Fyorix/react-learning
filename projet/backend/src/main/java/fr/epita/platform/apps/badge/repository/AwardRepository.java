package fr.epita.platform.apps.badge.repository;

import fr.epita.platform.apps.badge.entity.Award;
import fr.epita.platform.apps.badge.entity.Tier;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AwardRepository extends JpaRepository<Award, UUID> {

    List<Award> findByUserIdOrderByCreatedAtDesc(UUID userId);

    boolean existsByBadgeIdAndUserIdAndTier(UUID badgeId, UUID userId, Tier tier);

    List<Award> findByCreatedAtBeforeOrderByCreatedAtDesc(Instant before, Pageable page);

    @Query("select a.userId, sum(a.points) from Award a group by a.userId order by sum(a.points) desc")
    List<Object[]> xpRanking(Pageable page);

    @Query("select coalesce(sum(a.points), 0) from Award a where a.userId = :u")
    long xpOf(@Param("u") UUID userId);
}
