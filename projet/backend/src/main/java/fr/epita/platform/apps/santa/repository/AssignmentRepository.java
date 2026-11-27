package fr.epita.platform.apps.santa.repository;

import fr.epita.platform.apps.santa.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByEventIdAndGiverId(UUID eventId, UUID giverId);

    Optional<Assignment> findByEventIdAndReceiverId(UUID eventId, UUID receiverId);

    long countByEventId(UUID eventId);

    @Modifying
    @Query("delete from Assignment a where a.eventId = :eventId")
    void deleteByEventId(UUID eventId);
}
