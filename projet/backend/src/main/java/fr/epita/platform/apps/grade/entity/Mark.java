package fr.epita.platform.apps.grade.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "grade_marks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Mark {

    @EmbeddedId
    private Key id;
    private BigDecimal value;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarkStatus status;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "assessment_id")
        private UUID assessmentId;
        @Column(name = "student_id")
        private UUID studentId;
    }
}
