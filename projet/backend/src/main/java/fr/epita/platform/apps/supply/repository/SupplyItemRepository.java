package fr.epita.platform.apps.supply.repository;

import fr.epita.platform.apps.supply.entity.SupplyItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SupplyItemRepository extends JpaRepository<SupplyItem, UUID> {

    List<SupplyItem> findAllByOrderByCategoryAscNameAsc();

    boolean existsByNameIgnoreCase(String name);

    /** Verrou des articles d'une demande, dans un ordre stable (pas d'interblocage). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from SupplyItem i where i.id in :ids order by i.id")
    List<SupplyItem> lock(@Param("ids") Collection<UUID> ids);
}
