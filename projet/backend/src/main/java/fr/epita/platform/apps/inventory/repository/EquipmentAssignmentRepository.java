package fr.epita.platform.apps.inventory.repository;

import fr.epita.platform.apps.inventory.entity.EquipmentAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EquipmentAssignmentRepository extends JpaRepository<EquipmentAssignment, UUID> {

    List<EquipmentAssignment> findByEquipmentIdOrderByAssignedAtDesc(UUID equipmentId);

    Optional<EquipmentAssignment> findByEquipmentIdAndReleasedAtIsNull(UUID equipmentId);
}
