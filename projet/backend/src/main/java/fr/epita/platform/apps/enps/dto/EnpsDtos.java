package fr.epita.platform.apps.enps.dto;

import fr.epita.platform.apps.enps.entity.EnpsQuestionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EnpsDtos {

    private EnpsDtos() {
    }

    @Schema(description = "Équipe (ADMIN)")
    public record TeamRequest(
            @Schema(description = "Nom unique, 80 caractères maximum", example = "Support client")
            @NotBlank @Size(max = 80) String name) {
    }

    @Schema(description = "Membres d'une équipe (remplace la liste ; un salarié appartient à une seule équipe)")
    public record MembersRequest(
            @Schema(description = "Salariés de l'équipe")
            @NotNull @Size(max = 500) List<UUID> userIds) {
    }

    @Schema(description = "Question complémentaire")
    public record QuestionRequest(
            @Schema(description = "Intitulé, 300 caractères maximum", example = "Qu'est-ce qui améliorerait votre quotidien ?")
            @NotBlank @Size(max = 300) String label,
            @Schema(description = "SCORE_0_10 ou TEXT", example = "TEXT")
            @NotNull EnpsQuestionType type) {
    }

    @Schema(description = "Nouvelle enquête : la question eNPS est ajoutée automatiquement en première position")
    public record SurveyRequest(
            @Schema(description = "Titre, 150 caractères maximum", example = "Baromètre T4")
            @NotBlank @Size(max = 150) String title,
            @Schema(description = "Ouverture (ISO-8601)", example = "2026-12-01T08:00:00Z")
            @NotNull Instant opensAt,
            @Schema(description = "Clôture (ISO-8601), après l'ouverture et dans le futur", example = "2026-12-15T18:00:00Z")
            @NotNull Instant closesAt,
            @Schema(description = "Questions complémentaires, 10 maximum")
            @NotNull @Size(max = 10) List<@Valid QuestionRequest> questions) {
    }

    @Schema(description = "Réponse à une question : `score` (0 à 10) ou `text`")
    public record AnswerRequest(
            @Schema(description = "Question", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID questionId,
            @Schema(description = "Note de 0 à 10 (questions SCORE_0_10)", example = "8")
            @Min(0) @Max(10) Integer score,
            @Schema(description = "Texte libre, 2 000 caractères maximum (questions TEXT, facultatif)", example = "Plus de télétravail")
            @Size(max = 2000) String text) {
    }

    @Schema(description = "Réponse anonyme à une enquête ; la question eNPS est obligatoire")
    public record ResponseRequest(
            @Schema(description = "Réponses")
            @NotEmpty @Size(max = 11) List<@Valid AnswerRequest> answers) {
    }

    @Schema(description = "Équipe")
    public record TeamDto(UUID id, String name, List<UUID> memberIds) {
    }

    @Schema(description = "Question")
    public record QuestionDto(UUID id, int position, String label, EnpsQuestionType type, boolean enps) {
    }

    @Schema(description = "Enquête ; `answered` : l'utilisateur courant a déjà répondu")
    public record SurveyDto(UUID id, String title, Instant opensAt, Instant closesAt, String state, boolean answered,
                            long participants, List<QuestionDto> questions) {
    }

    @Schema(description = "Résultat agrégé d'une question note : moyenne et répartition 0..10")
    public record ScoreResult(UUID questionId, String label, double average, Map<Integer, Long> distribution) {
    }

    @Schema(description = "Verbatims d'une question texte, mélangés et sans date")
    public record TextResult(UUID questionId, String label, List<String> answers) {
    }

    @Schema(description = """
            Résultats d'un périmètre (global ou équipe). Sous 5 réponses : `insufficientResponses` vrai et aucun détail.""")
    public record ScopeResult(UUID teamId, String teamName, long responses, boolean insufficientResponses, Integer enps,
                              Integer promoters, Integer passives, Integer detractors, List<ScoreResult> scores, List<TextResult> texts) {
    }

    @Schema(description = "Résultats d'une enquête")
    public record ResultsDto(UUID surveyId, String title, ScopeResult global, List<ScopeResult> teams) {
    }

    @Schema(description = "eNPS d'une enquête dans la tendance (null sous 5 réponses)")
    public record TrendPoint(UUID surveyId, String title, Instant closesAt, long responses, Integer enps) {
    }

    @Schema(description = "Personne à relancer (jamais ses réponses)")
    public record NonRespondentDto(UUID userId, String name, String teamName) {
    }
}
