package fr.epita.platform.apps.jargon.repository;

import fr.epita.platform.apps.jargon.entity.ContributionStatus;
import fr.epita.platform.apps.jargon.entity.JargonTerm;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JargonTermRepository extends JpaRepository<JargonTerm, UUID> {

    boolean existsByNormalizedAndStatusNot(String normalized, ContributionStatus status);

    List<JargonTerm> findByStatusOrderByNormalizedAsc(ContributionStatus status);

    List<JargonTerm> findByStatusOrderByCreatedAtAsc(ContributionStatus status);

    List<JargonTerm> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);

    @Query("select t from JargonTerm t where t.status = 'PUBLISHED' and t.normalized like concat(:prefix, '%') order by t.normalized")
    List<JargonTerm> suggest(@Param("prefix") String prefix, Pageable page);
}
