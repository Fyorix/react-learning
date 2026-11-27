package fr.epita.platform.apps.pomodoro.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pomodoro_rooms")
@Getter
@Setter
@NoArgsConstructor
public class PomodoroRoom extends BaseEntity {

    @Column(nullable = false)
    private String name;
    @Column(name = "host_id", nullable = false)
    private UUID hostId;
    @Column(name = "focus_minutes", nullable = false)
    private int focusMinutes = 25;
    @Column(name = "short_break_minutes", nullable = false)
    private int shortBreakMinutes = 5;
    @Column(name = "long_break_minutes", nullable = false)
    private int longBreakMinutes = 15;
    @Column(name = "cycles_before_long_break", nullable = false)
    private int cyclesBeforeLongBreak = 4;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Phase phase = Phase.IDLE;
    @Enumerated(EnumType.STRING)
    @Column(name = "paused_phase")
    private Phase pausedPhase;
    @Column(name = "phase_started_at")
    private Instant phaseStartedAt;
    @Column(name = "phase_ends_at")
    private Instant phaseEndsAt;
    @Column(name = "paused_remaining_seconds")
    private Long pausedRemainingSeconds;
    @Column(nullable = false)
    private int cycle;

    public Duration durationOf(Phase p) {
        return Duration.ofMinutes(switch (p) {
            case FOCUS -> focusMinutes;
            case SHORT_BREAK -> shortBreakMinutes;
            case LONG_BREAK -> longBreakMinutes;
            default -> 0;
        });
    }
}
