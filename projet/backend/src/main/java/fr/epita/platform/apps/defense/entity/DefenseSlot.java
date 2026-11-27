package fr.epita.platform.apps.defense.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "defense_slots")
@Getter
@Setter
@NoArgsConstructor
public class DefenseSlot extends BaseEntity {

    @Column(name = "start_at", nullable = false)
    private Instant startAt;
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;
    @Column(nullable = false)
    private String room;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "defense_jury", joinColumns = @JoinColumn(name = "slot_id"))
    @Column(name = "teacher_id")
    private Set<UUID> juryIds = new HashSet<>();

    public Instant endAt() {
        return startAt.plusSeconds(durationMinutes * 60L);
    }

    public boolean overlaps(DefenseSlot o) {
        return startAt.isBefore(o.endAt()) && o.startAt.isBefore(endAt());
    }
}
