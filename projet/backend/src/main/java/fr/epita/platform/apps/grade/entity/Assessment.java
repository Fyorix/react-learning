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
@Table(name = "grade_assessments")
@Getter
@Setter
@NoArgsConstructor
public class Assessment extends BaseEntity {

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;
    @Column(name = "period_id", nullable = false)
    private UUID periodId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private LocalDate date;
    @Column(nullable = false)
    private BigDecimal weight;
}
