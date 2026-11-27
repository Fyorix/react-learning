package fr.epita.platform.apps.invoice.repository;

import fr.epita.platform.apps.invoice.entity.Reminder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    List<Reminder> findByInvoiceIdOrderByLevelAsc(UUID invoiceId);
}
