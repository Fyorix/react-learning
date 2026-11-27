package fr.epita.platform.apps.skills.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** Binôme mentor / apprenant sur une compétence. */
@Entity
@Table(name = "skills_pairs")
@Getter
@Setter
@NoArgsConstructor
public class Pair extends BaseEntity {

    @Column(name = "mentor_id", nullable = false)
    private UUID mentorId;
    @Column(name = "learner_id", nullable = false)
    private UUID learnerId;
    @Column(name = "skill_id", nullable = false)
    private UUID skillId;
    /** ACTIVE ou ENDED */
    @Column(nullable = false)
    private String status = "ACTIVE";
    @Column(name = "ended_at")
    private Instant endedAt;

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
