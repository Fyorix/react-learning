package fr.epita.platform.apps.pomodoro.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pomodoro_completed")
@Getter
@Setter
@NoArgsConstructor
public class CompletedPomodoro extends BaseEntity {

    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;
}
