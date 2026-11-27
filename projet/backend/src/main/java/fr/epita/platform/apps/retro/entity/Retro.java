package fr.epita.platform.apps.retro.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "retros")
@Getter
@Setter
@NoArgsConstructor
public class Retro extends BaseEntity {

    @Column(name = "team_id", nullable = false)
    private UUID teamId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String sprint;
    @Column(name = "facilitator_id", nullable = false)
    private UUID facilitatorId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RetroPhase phase;
    @Column(name = "timer_ends_at")
    private Instant timerEndsAt;
    @Column(name = "timer_remaining")
    private Integer timerRemaining;
}
