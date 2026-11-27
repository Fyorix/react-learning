package fr.epita.platform.apps.license.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "license_seats")
@Getter
@Setter
@NoArgsConstructor
public class Seat extends BaseEntity {

    @Column(name = "software_id", nullable = false)
    private UUID softwareId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "last_used_on")
    private LocalDate lastUsedOn;
}
