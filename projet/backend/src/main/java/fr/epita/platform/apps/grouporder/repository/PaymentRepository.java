package fr.epita.platform.apps.grouporder.repository;

import fr.epita.platform.apps.grouporder.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, Payment.Key> {

    List<Payment> findByIdOrderId(UUID orderId);
}
