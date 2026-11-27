package fr.epita.platform.apps.grade.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "grade_subjects")
@Getter
@Setter
@NoArgsConstructor
public class Subject extends BaseEntity {

    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private BigDecimal coefficient;
    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;
    @Column(name = "class_id", nullable = false)
    private UUID classId;
}
