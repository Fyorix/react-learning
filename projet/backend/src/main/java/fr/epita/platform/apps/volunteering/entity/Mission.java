package fr.epita.platform.apps.volunteering.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "volunteering_missions")
@Getter
@Setter
@NoArgsConstructor
public class Mission extends BaseEntity {

    @Column(name = "association_id", nullable = false)
    private UUID associationId;
    @Column(nullable = false)
    private String title;
    @Column(name = "mission_date", nullable = false)
    private LocalDate missionDate;
    @Column(name = "duration_hours", nullable = false)
    private int durationHours;
    @Column(nullable = false)
    private String location;
    /** Compétences séparées par des virgules. */
    @Column(nullable = false)
    private String skills = "";
    @Column(nullable = false)
    private int seats;

    public List<String> skillList() {
        return skills.isBlank() ? List.of() : Arrays.asList(skills.split(","));
    }
}
