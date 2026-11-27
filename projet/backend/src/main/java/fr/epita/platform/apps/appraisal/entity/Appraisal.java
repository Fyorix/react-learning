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
@Table(name = "appraisals")
@Getter
@Setter
@NoArgsConstructor
public class Appraisal extends BaseEntity {

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;
    @Column(name = "employee_id", nullable = false)
    private UUID employeeId;
    @Column(name = "manager_id", nullable = false)
    private UUID managerId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Step step;
    @Column(name = "final_comment")
    private String finalComment;
    @Column(name = "reopened_until")
    private Instant reopenedUntil;
    @Column(name = "self_submitted_at")
    private Instant selfSubmittedAt;
    @Column(name = "manager_submitted_at")
    private Instant managerSubmittedAt;
    @Column(name = "employee_signed_at")
    private Instant employeeSignedAt;
    @Column(name = "manager_signed_at")
    private Instant managerSignedAt;
}
