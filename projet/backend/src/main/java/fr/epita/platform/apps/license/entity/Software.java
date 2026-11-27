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
@Table(name = "license_software")
@Getter
@Setter
@NoArgsConstructor
public class Software extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
    @Column(nullable = false)
    private String vendor;
    @Column(nullable = false)
    private int seats;
    @Column(name = "cost_per_seat", nullable = false)
    private BigDecimal costPerSeat;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Billing billing;
    @Column(name = "renewal_date", nullable = false)
    private LocalDate renewalDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SoftwareStatus status;
}
