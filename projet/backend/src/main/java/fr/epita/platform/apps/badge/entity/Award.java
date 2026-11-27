package fr.epita.platform.apps.badge.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "badge_awards")
@Getter
@Setter
@NoArgsConstructor
public class Award extends BaseEntity {

    @Column(name = "badge_id", nullable = false)
    private UUID badgeId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Tier tier;
    @Column(nullable = false)
    private int points;
    private String reason;
    @Column(name = "awarded_by")
    private UUID awardedBy;
}
