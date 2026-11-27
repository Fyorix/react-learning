package fr.epita.platform.apps.appraisal.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "appraisal_campaigns")
@Getter
@Setter
@NoArgsConstructor
public class AppraisalCampaign extends BaseEntity {

    @Column(nullable = false, unique = true)
    private int year;
    @Column(nullable = false)
    private LocalDate deadline;
}
