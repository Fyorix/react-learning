package fr.epita.platform.apps.grouporder.repository;

import fr.epita.platform.apps.grouporder.entity.OrderLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderLineRepository extends JpaRepository<OrderLine, UUID> {

    List<OrderLine> findByOrderIdOrderByCreatedAtAsc(UUID orderId);

    List<OrderLine> findByOrderIdAndUserId(UUID orderId, UUID userId);
}
