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
@Table(name = "access_grants")
@Getter
@Setter
@NoArgsConstructor
public class AccessGrant extends BaseEntity {

    @Column(name = "tool_id", nullable = false)
    private UUID toolId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessLevel level;
    @Column(name = "request_id")
    private UUID requestId;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GrantStatus status;
    @Column(name = "last_confirmed_at", nullable = false)
    private Instant lastConfirmedAt;
    @Column(name = "ended_at")
    private Instant endedAt;
}
