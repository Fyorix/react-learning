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
@Table(name = "association_registrations")
@Getter
@Setter
@NoArgsConstructor
public class AssociationRegistration extends BaseEntity {

    @Column(name = "activity_id", nullable = false)
    private UUID activityId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssociationRegistrationStatus status;
}
