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
@Table(name = "recruitment_applications")
@Getter
@Setter
@NoArgsConstructor
public class Application extends BaseEntity {

    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "first_name", nullable = false)
    private String firstName;
    @Column(name = "last_name", nullable = false)
    private String lastName;
    @Column(nullable = false)
    private String email;
    private String phone;
    @Column(name = "cv_key", nullable = false)
    private String cvKey;
    @Column(name = "cv_name")
    private String cvName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Stage stage;
    @Column(name = "stage_since", nullable = false)
    private Instant stageSince;
    @Column(name = "hired_at")
    private Instant hiredAt;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "recruitment_interviewers", joinColumns = @JoinColumn(name = "application_id"))
    @Column(name = "user_id")
    private Set<UUID> interviewerIds = new HashSet<>();
}
