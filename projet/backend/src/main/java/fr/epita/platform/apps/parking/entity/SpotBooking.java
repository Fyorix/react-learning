package fr.epita.platform.apps.parking.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "parking_bookings")
@Getter
@Setter
@NoArgsConstructor
public class SpotBooking extends BaseEntity {

    @Column(name = "spot_id", nullable = false)
    private UUID spotId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private LocalDate day;
}
