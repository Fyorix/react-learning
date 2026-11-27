package fr.epita.platform.apps.library.repository;

import fr.epita.platform.apps.library.entity.QueueEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, UUID> {

    List<QueueEntry> findByBookIdOrderByJoinedAtAsc(UUID bookId);

    Optional<QueueEntry> findByBookIdAndUserId(UUID bookId, UUID userId);

    List<QueueEntry> findAll();
}
