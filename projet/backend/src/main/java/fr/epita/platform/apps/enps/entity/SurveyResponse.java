package fr.epita.platform.apps.enps.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** Ce qui a été répondu — ne garde que l'équipe du répondant, sans date ni identifiant de personne. */
@Entity
@Table(name = "enps_responses")
@Getter
@Setter
@NoArgsConstructor
public class SurveyResponse {

    @Id
    private UUID id = UUID.randomUUID();
    @Column(name = "survey_id", nullable = false)
    private UUID surveyId;
    @Column(name = "team_id")
    private UUID teamId;
}
