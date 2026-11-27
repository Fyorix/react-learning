package fr.epita.platform.apps.referral.entity;

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
@Table(name = "referrals")
@Getter
@Setter
@NoArgsConstructor
public class Referral extends BaseEntity {

    @Column(name = "job_id", nullable = false)
    private UUID jobId;
    @Column(name = "referrer_id", nullable = false)
    private UUID referrerId;
    @Column(name = "candidate_name", nullable = false)
    private String candidateName;
    @Column(name = "candidate_email", nullable = false)
    private String candidateEmail;
    @Column(name = "linkedin_url")
    private String linkedinUrl;
    @Column(nullable = false)
    private String message;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReferralStatus status;
    @Column(name = "internal_comment")
    private String internalComment;
    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;
    @Column(name = "bonus_paid_at")
    private Instant bonusPaidAt;
}
