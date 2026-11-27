package fr.epita.platform.apps.vehicle.entity;

import fr.epita.platform.apps.vehicle.entity.VehicleEnums.BookingStatus;
import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vehicle_bookings")
@Getter
@Setter
@NoArgsConstructor
public class VehicleBooking extends BaseEntity {

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;
    @Column(nullable = false)
    private String purpose;
    @Column(nullable = false)
    private String destination;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus status = BookingStatus.BOOKED;
    @Column(name = "start_mileage")
    private Integer startMileage;
    @Column(name = "end_mileage")
    private Integer endMileage;

    public boolean blocksVehicle() {
        return status == BookingStatus.BOOKED || status == BookingStatus.OUT;
    }
}
