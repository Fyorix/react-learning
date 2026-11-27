package fr.epita.platform.apps.training.entity;

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
@Table(name = "training_enrollments")
@Getter
@Setter
@NoArgsConstructor
public class Enrollment extends BaseEntity {

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnrollmentStatus status = EnrollmentStatus.REQUESTED;
    private String reason;
    /** Date de l'accord du manager : fixe l'ordre de la liste d'attente. */
    @Column(name = "approved_at")
    private Instant approvedAt;
    private Boolean present;
}
