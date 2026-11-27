package fr.epita.platform.apps.skills.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "skills_sessions")
@Getter
@Setter
@NoArgsConstructor
public class MentoringSession extends BaseEntity {

    @Column(name = "pair_id", nullable = false)
    private UUID pairId;
    @Column(name = "author_id", nullable = false)
    private UUID authorId;
    @Column(name = "session_date", nullable = false)
    private LocalDate date;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    private String notes;
}
