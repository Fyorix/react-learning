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
@Table(name = "appraisal_answers")
@Getter
@Setter
@NoArgsConstructor
public class AppraisalAnswer extends BaseEntity {

    @Column(name = "appraisal_id", nullable = false)
    private UUID appraisalId;
    @Column(name = "question_id", nullable = false)
    private UUID questionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Author author;
    @Column(name = "text_value")
    private String textValue;
    @Column(name = "rating_value")
    private Integer ratingValue;
}
