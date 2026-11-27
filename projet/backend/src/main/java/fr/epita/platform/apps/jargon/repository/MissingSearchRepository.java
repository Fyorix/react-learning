package fr.epita.platform.apps.jargon.repository;

import fr.epita.platform.apps.jargon.entity.MissingSearch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MissingSearchRepository extends JpaRepository<MissingSearch, String> {

    List<MissingSearch> findTop50ByOrderByHitsDescLastAtDesc();

    /** Incrément atomique (upsert) : deux recherches simultanées comptent toutes les deux. */
    @Modifying
    @Query(value = """
            insert into jargon_missing (normalized, query, hits, last_at) values (:n, :q, 1, now())
            on conflict (normalized) do update set hits = jargon_missing.hits + 1, last_at = now()
            """, nativeQuery = true)
    void hit(@Param("n") String normalized, @Param("q") String query);
}
