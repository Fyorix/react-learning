package fr.epita.platform.apps.grouporder.repository;

import fr.epita.platform.apps.grouporder.entity.GroupOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupOrderRepository extends JpaRepository<GroupOrder, UUID> {

    List<GroupOrder> findAllByOrderByDeadlineDesc();

    /** Verrou de la commande : sérialise modifications de lignes et clôture. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from GroupOrder o where o.id = :id")
    Optional<GroupOrder> lock(@Param("id") UUID id);
}
