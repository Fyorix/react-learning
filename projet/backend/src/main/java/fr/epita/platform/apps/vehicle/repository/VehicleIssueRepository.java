package fr.epita.platform.apps.vehicle.repository;

import fr.epita.platform.apps.vehicle.entity.VehicleIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleIssueRepository extends JpaRepository<VehicleIssue, UUID> {

    List<VehicleIssue> findByResolvedFalseOrderByCreatedAtDesc();

    List<VehicleIssue> findByVehicleIdAndResolvedFalse(UUID vehicleId);
}
