package fr.epita.platform.apps.enps.entity;

import fr.epita.platform.common.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "enps_questions")
@Getter
@Setter
@NoArgsConstructor
public class SurveyQuestion extends BaseEntity {

    @Column(name = "survey_id", nullable = false)
    private UUID surveyId;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false)
    private String label;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EnpsQuestionType type;
}
