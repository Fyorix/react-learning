package fr.epita.platform.apps.kb.repository;

import fr.epita.platform.apps.kb.entity.KbArticle;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KbArticleRepository extends JpaRepository<KbArticle, UUID> {

    Optional<KbArticle> findBySlug(String slug);

    boolean existsBySlug(String slug);

    List<KbArticle> findByPublishedVersionNotNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from KbArticle a where a.slug = :slug")
    Optional<KbArticle> lock(@Param("slug") String slug);

    @Modifying
    @Query("update KbArticle a set a.views = a.views + 1 where a.id = :id")
    void incrementViews(@Param("id") UUID id);
}
