package fr.epita.platform.apps.vehicle.repository;

import fr.epita.platform.apps.vehicle.entity.VehicleBooking;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VehicleBookingRepository extends JpaRepository<VehicleBooking, UUID> {

    List<VehicleBooking> findByVehicleIdOrderByStartsAtAsc(UUID vehicleId);

    List<VehicleBooking> findByUserIdOrderByStartsAtDesc(UUID userId);
}
