package fr.epita.platform.apps.santa.repository;

import fr.epita.platform.apps.santa.entity.Exclusion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ExclusionRepository extends JpaRepository<Exclusion, UUID> {

    List<Exclusion> findByEventId(UUID eventId);

    @Modifying
    @Query("delete from Exclusion e where e.eventId = :eventId")
    void deleteByEventId(UUID eventId);

    @Modifying
    @Query("delete from Exclusion e where e.eventId = :eventId and (e.giverId = :userId or e.receiverId = :userId)")
    void deleteInvolving(UUID eventId, UUID userId);
}
