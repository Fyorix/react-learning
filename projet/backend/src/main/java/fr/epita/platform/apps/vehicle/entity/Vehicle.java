package fr.epita.platform.apps.vehicle.entity;

import fr.epita.platform.apps.vehicle.entity.VehicleEnums.VehicleStatus;
import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vehicles")
@Getter
@Setter
@NoArgsConstructor
public class Vehicle extends BaseEntity {

    public static final int SERVICE_INTERVAL_KM = 15_000;

    @Column(nullable = false, unique = true)
    private String plate;
    @Column(nullable = false)
    private String model;
    @Column(nullable = false)
    private int seats;
    @Column(name = "license_type", nullable = false)
    private String licenseType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VehicleStatus status = VehicleStatus.AVAILABLE;
    @Column(nullable = false)
    private int mileage;
    @Column(name = "last_service_mileage", nullable = false)
    private int lastServiceMileage;

    public boolean serviceDue() {
        return mileage - lastServiceMileage >= SERVICE_INTERVAL_KM;
    }
}
