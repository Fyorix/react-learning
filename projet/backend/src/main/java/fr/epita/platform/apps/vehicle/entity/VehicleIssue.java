package fr.epita.platform.apps.vehicle.entity;

import fr.epita.platform.apps.vehicle.entity.VehicleEnums.Severity;
import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "vehicle_issues")
@Getter
@Setter
@NoArgsConstructor
public class VehicleIssue extends BaseEntity {

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;
    @Column(name = "booking_id")
    private UUID bookingId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private boolean resolved;
}
