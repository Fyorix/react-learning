package fr.epita.platform.apps.grade.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Appréciation d'une matière (enseignant) ou générale si {@code subjectId} est nul (ADMIN). */
@Entity
@Table(name = "grade_comments")
@Getter
@Setter
@NoArgsConstructor
public class GradeComment {

    @Id
    private UUID id;
    @Column(name = "period_id", nullable = false)
    private UUID periodId;
    @Column(name = "student_id", nullable = false)
    private UUID studentId;
    @Column(name = "subject_id")
    private UUID subjectId;
    @Column(nullable = false)
    private String text;
}
