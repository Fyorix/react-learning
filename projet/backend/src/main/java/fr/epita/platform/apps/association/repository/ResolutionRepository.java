package fr.epita.platform.apps.association.repository;

import fr.epita.platform.apps.association.entity.Resolution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ResolutionRepository extends JpaRepository<Resolution, UUID> {

    List<Resolution> findByAssemblyIdOrderByPositionAsc(UUID assemblyId);

    /** Incréments atomiques du décompte. */
    @Modifying
    @Query("update Resolution r set r.forCount = r.forCount + 1 where r.id = :id")
    void addFor(@Param("id") UUID id);

    @Modifying
    @Query("update Resolution r set r.againstCount = r.againstCount + 1 where r.id = :id")
    void addAgainst(@Param("id") UUID id);

    @Modifying
    @Query("update Resolution r set r.abstainCount = r.abstainCount + 1 where r.id = :id")
    void addAbstain(@Param("id") UUID id);
}
