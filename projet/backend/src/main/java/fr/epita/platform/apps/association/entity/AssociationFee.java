package fr.epita.platform.apps.association.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "association_fees")
@Getter
@Setter
@NoArgsConstructor
public class AssociationFee extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private String season;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeeCategory category;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(name = "paid_at", nullable = false)
    private LocalDate paidAt;
    @Column(name = "recorded_by")
    private UUID recordedBy;
}
