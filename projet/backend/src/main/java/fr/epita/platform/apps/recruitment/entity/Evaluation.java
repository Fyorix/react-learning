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
@Table(name = "recruitment_evaluations")
@Getter
@Setter
@NoArgsConstructor
public class Evaluation extends BaseEntity {

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;
    @Column(name = "evaluator_id", nullable = false)
    private UUID evaluatorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stage stage;
    @Column(nullable = false)
    private int rating;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Recommendation recommendation;
    @Column(nullable = false)
    private String comment;
}
