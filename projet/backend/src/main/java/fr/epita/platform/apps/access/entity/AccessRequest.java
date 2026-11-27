package fr.epita.platform.apps.access.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "access_requests")
@Getter
@Setter
@NoArgsConstructor
public class AccessRequest extends BaseEntity {

    @Column(name = "tool_id", nullable = false)
    private UUID toolId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessLevel level;
    @Column(nullable = false)
    private String justification;
    @Column(name = "duration_days", nullable = false)
    private int durationDays;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessRequestStatus status;
    @Column(name = "manager_id")
    private UUID managerId;
    private String reason;
    @Column(name = "decided_by")
    private UUID decidedBy;
}
