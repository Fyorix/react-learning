package fr.epita.platform.apps.inventory.repository;

import fr.epita.platform.apps.inventory.entity.Equipment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {

    List<Equipment> findAllByOrderByTypeAscBrandAscModelAsc();

    List<Equipment> findByHolderIdOrderByTypeAsc(UUID holderId);

    boolean existsByHolderId(UUID holderId);

    boolean existsBySerialNumberIgnoreCase(String serialNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Equipment e where e.id = :id")
    Optional<Equipment> lockById(UUID id);
}
