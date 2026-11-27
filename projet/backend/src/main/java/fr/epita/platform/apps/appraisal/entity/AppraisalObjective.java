package fr.epita.platform.apps.appraisal.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "appraisal_objectives")
@Getter
@Setter
@NoArgsConstructor
public class AppraisalObjective extends BaseEntity {

    @Column(name = "appraisal_id", nullable = false)
    private UUID appraisalId;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false)
    private String title;
    private String description;
}
