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
@Table(name = "timetable_courses")
@Getter
@Setter
@NoArgsConstructor
public class TimetableCourse extends BaseEntity {

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;
    @Column(name = "group_id", nullable = false)
    private UUID groupId;
    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;
    @Column(name = "room_id", nullable = false)
    private UUID roomId;
    @Column(name = "start_at", nullable = false)
    private Instant startAt;
    @Column(name = "end_at", nullable = false)
    private Instant endAt;
}
