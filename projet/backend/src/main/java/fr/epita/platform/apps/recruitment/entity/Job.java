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
@Table(name = "recruitment_jobs")
@Getter
@Setter
@NoArgsConstructor
public class Job extends BaseEntity {

    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String team;
    @Column(nullable = false)
    private String description;
    @Column(nullable = false)
    private String location;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobStatus status;
    @Column(name = "created_by")
    private UUID createdBy;
}
