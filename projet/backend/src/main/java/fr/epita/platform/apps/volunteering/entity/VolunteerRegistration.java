package fr.epita.platform.apps.volunteering.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "volunteering_registrations")
@Getter
@Setter
@NoArgsConstructor
public class VolunteerRegistration extends BaseEntity {

    @Column(name = "mission_id", nullable = false)
    private UUID missionId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VolunteerStatus status;
    @Column(name = "decided_by")
    private UUID decidedBy;
    private String reason;
    private BigDecimal hours;
    private String comment;
}
