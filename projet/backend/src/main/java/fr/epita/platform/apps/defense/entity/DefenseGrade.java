package fr.epita.platform.apps.defense.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "defense_grades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DefenseGrade {

    @EmbeddedId
    private Key id;
    @Column(nullable = false)
    private BigDecimal grade;
    @Column(nullable = false)
    private String comment;

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        @Column(name = "slot_id")
        private UUID slotId;
        @Column(name = "teacher_id")
        private UUID teacherId;
    }
}
