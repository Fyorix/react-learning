package fr.epita.platform.apps.invoice.repository;

import fr.epita.platform.apps.invoice.entity.CreditNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    List<CreditNote> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);
}
