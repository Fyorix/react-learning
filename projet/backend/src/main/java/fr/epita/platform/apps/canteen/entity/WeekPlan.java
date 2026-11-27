package fr.epita.platform.apps.canteen.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "canteen_weeks")
@Getter
@Setter
@NoArgsConstructor
public class WeekPlan {

    @Id
    private UUID id;

    /** Semaine ISO, ex. 2026-W47. */
    @Column(nullable = false, unique = true)
    private String week;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WeekStatus status = WeekStatus.DRAFT;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "weekPlan", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DayMenu> days = new ArrayList<>();

    @PrePersist
    @PreUpdate
    void touch() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        updatedAt = Instant.now();
    }

    public Optional<DayMenu> day(DayOfWeek day) {
        return days.stream().filter(d -> d.getDayOfWeek() == day).findFirst();
    }
}
