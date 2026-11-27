package fr.epita.platform.apps.fitness.entity;

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
@Table(name = "fitness_classes")
@Getter
@Setter
@NoArgsConstructor
public class FitnessClass extends BaseEntity {

    @Column(nullable = false)
    private String type;
    @Column(name = "coach_id", nullable = false)
    private UUID coachId;
    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    @Column(nullable = false)
    private int capacity;
    @Column(nullable = false)
    private boolean cancelled;

    public Instant endsAt() {
        return startsAt.plusSeconds(durationMinutes * 60L);
    }
}
