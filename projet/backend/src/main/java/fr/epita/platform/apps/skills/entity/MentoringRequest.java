package fr.epita.platform.apps.skills.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "skills_requests")
@Getter
@Setter
@NoArgsConstructor
public class MentoringRequest extends BaseEntity {

    public static final Duration EXPIRY = Duration.ofDays(14);

    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;
    @Column(name = "mentor_id", nullable = false)
    private UUID mentorId;
    @Column(name = "skill_id", nullable = false)
    private UUID skillId;
    private String message;
    /** PENDING, ACCEPTED, DECLINED ; EXPIRED est calculé */
    @Column(nullable = false)
    private String status = "PENDING";
    @Column(name = "decided_at")
    private Instant decidedAt;

    public String effectiveStatus(Instant now) {
        return "PENDING".equals(status) && getCreatedAt().plus(EXPIRY).isBefore(now) ? "EXPIRED" : status;
    }
}
