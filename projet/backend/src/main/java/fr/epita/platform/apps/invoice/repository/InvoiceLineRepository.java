package fr.epita.platform.apps.invoice.repository;

import fr.epita.platform.apps.invoice.entity.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

    List<InvoiceLine> findByInvoiceIdOrderByPositionAsc(UUID invoiceId);

    void deleteByInvoiceId(UUID invoiceId);
}
