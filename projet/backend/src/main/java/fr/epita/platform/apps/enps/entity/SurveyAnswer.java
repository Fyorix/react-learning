package fr.epita.platform.apps.enps.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "enps_answers")
@Getter
@Setter
@NoArgsConstructor
public class SurveyAnswer {

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "response_id", nullable = false)
    private UUID responseId;
    @Column(name = "question_id", nullable = false)
    private UUID questionId;
    private Integer score;
    private String text;
}
