package fr.epita.platform.apps.timetable.entity;

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
@Table(name = "timetable_subjects")
@Getter
@Setter
@NoArgsConstructor
public class TimetableSubject extends BaseEntity {

    @Column(nullable = false)
    private String name;
    @Column(name = "planned_hours", nullable = false)
    private int plannedHours;
    @Column(name = "group_id", nullable = false)
    private UUID groupId;
}
