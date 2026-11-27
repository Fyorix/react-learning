package fr.epita.platform.apps.badge.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "badge_events")
@Getter
@Setter
@NoArgsConstructor
public class ActivityEvent extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(nullable = false)
    private String type;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
