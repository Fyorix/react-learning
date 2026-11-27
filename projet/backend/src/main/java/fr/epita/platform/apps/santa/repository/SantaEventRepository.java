package fr.epita.platform.apps.santa.repository;

import fr.epita.platform.apps.santa.entity.SantaEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SantaEventRepository extends JpaRepository<SantaEvent, UUID> {

    List<SantaEvent> findByIdInOrderByEventDateAsc(Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SantaEvent e where e.id = :id")
    Optional<SantaEvent> lockById(UUID id);
}
