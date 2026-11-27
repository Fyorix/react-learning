package fr.epita.platform.apps.recruitment.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "recruitment_stage_events")
@Getter
@Setter
@NoArgsConstructor
public class StageEvent extends BaseEntity {

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;
    @Enumerated(EnumType.STRING)
    @Column(name = "from_stage")
    private Stage fromStage;
    @Enumerated(EnumType.STRING)
    @Column(name = "to_stage", nullable = false)
    private Stage toStage;
    private String reason;
    @Column(name = "moved_by")
    private UUID movedBy;
}
