package fr.epita.platform.apps.access.repository;

import fr.epita.platform.apps.access.entity.AuditEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {

    List<AuditEntry> findAllByOrderByCreatedAtDesc();
}
