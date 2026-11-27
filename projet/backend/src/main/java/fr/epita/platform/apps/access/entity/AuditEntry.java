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
@Table(name = "access_audit")
@Getter
@Setter
@NoArgsConstructor
public class AuditEntry extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;
    @Column(name = "tool_id", nullable = false)
    private UUID toolId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessLevel level;
    @Column(name = "actor_id")
    private UUID actorId;
    private String detail;
}
