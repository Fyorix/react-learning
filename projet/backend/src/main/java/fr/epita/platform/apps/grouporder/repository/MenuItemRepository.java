package fr.epita.platform.apps.grouporder.repository;

import fr.epita.platform.apps.grouporder.entity.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    List<MenuItem> findBySupplierIdOrderByPositionAsc(UUID supplierId);

    List<MenuItem> findBySupplierIdAndArchivedFalseOrderByPositionAsc(UUID supplierId);
}
