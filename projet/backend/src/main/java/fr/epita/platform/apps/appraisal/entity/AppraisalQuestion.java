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
@Table(name = "appraisal_questions")
@Getter
@Setter
@NoArgsConstructor
public class AppraisalQuestion extends BaseEntity {

    @Column(name = "campaign_id", nullable = false)
    private UUID campaignId;
    @Column(nullable = false)
    private int position;
    @Column(nullable = false)
    private String question;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;
}
