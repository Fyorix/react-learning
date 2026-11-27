package fr.epita.platform.apps.kb.repository;

import fr.epita.platform.apps.kb.entity.KbVersion;
import fr.epita.platform.apps.kb.entity.VersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KbVersionRepository extends JpaRepository<KbVersion, UUID> {

    List<KbVersion> findByArticleIdOrderByNumberDesc(UUID articleId);

    Optional<KbVersion> findFirstByArticleIdOrderByNumberDesc(UUID articleId);

    Optional<KbVersion> findByArticleIdAndNumber(UUID articleId, int number);

    boolean existsByArticleIdAndStatus(UUID articleId, VersionStatus status);

    List<KbVersion> findByStatusOrderByCreatedAtAsc(VersionStatus status);

    List<KbVersion> findByAuthorIdOrderByCreatedAtDesc(UUID authorId);
}
