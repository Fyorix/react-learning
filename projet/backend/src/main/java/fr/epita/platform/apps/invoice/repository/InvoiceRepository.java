package fr.epita.platform.apps.invoice.repository;

import fr.epita.platform.apps.invoice.entity.Invoice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    List<Invoice> findAllByOrderByCreatedAtDesc();

    List<Invoice> findByClientIdAndIssuedAtNotNullOrderByIssuedAtDesc(UUID clientId);

    List<Invoice> findByIssuedAtNotNull();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Invoice i where i.id = :id")
    Optional<Invoice> lock(@Param("id") UUID id);
}
