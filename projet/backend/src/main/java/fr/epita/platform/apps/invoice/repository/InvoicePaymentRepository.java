package fr.epita.platform.apps.invoice.repository;

import fr.epita.platform.apps.invoice.entity.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID> {

    List<InvoicePayment> findByInvoiceIdOrderByPaidOnAsc(UUID invoiceId);
}
