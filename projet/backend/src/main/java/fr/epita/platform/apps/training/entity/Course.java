package fr.epita.platform.apps.training.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "training_courses")
@Getter
@Setter
@NoArgsConstructor
public class Course extends BaseEntity {

    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String theme;
    @Column(name = "trainer_id", nullable = false)
    private UUID trainerId;
    @Column(name = "duration_hours", nullable = false)
    private BigDecimal durationHours;
    @Column(nullable = false)
    private String description;
}
