package fr.epita.platform.apps.attendance.entity;

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
@Table(name = "attendance_groups")
@Getter
@Setter
@NoArgsConstructor
public class AttendanceGroup extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "attendance_group_students", joinColumns = @JoinColumn(name = "group_id"))
    @Column(name = "student_id")
    private Set<UUID> studentIds = new HashSet<>();
}
