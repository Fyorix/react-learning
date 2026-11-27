package fr.epita.platform.apps.appraisal.dto;

import fr.epita.platform.apps.appraisal.entity.QuestionType;
import fr.epita.platform.apps.appraisal.entity.Step;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AppraisalDtos {

    private AppraisalDtos() {
    }

    @Schema(description = "AppraisalQuestion du modèle d'entretien")
    public record QuestionRequest(
            @Schema(description = "Intitulé, 300 caractères maximum", example = "Quelles ont été vos principales réussites cette année ?")
            @NotBlank @Size(max = 300) String question,
            @Schema(description = "TEXT (réponse libre) ou RATING_1_5 (note entière de 1 à 5)", example = "TEXT")
            @NotNull QuestionType type) {
    }

    @Schema(description = "Lancement d'une campagne annuelle")
    public record CampaignRequest(
            @Schema(description = "Année (une campagne par année)", example = "2026")
            @Min(2000) @Max(2100) int year,
            @Schema(description = "Date limite de saisie, dans le futur", example = "2026-12-20")
            @NotNull LocalDate deadline,
            @Schema(description = "Modèle : 1 à 20 questions")
            @NotEmpty @Size(max = 20) List<@Valid QuestionRequest> template) {
    }

    @Schema(description = "Réponse à une question : `text` pour TEXT, `rating` (1 à 5) pour RATING_1_5")
    public record AnswerRequest(
            @Schema(description = "AppraisalQuestion", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID questionId,
            @Schema(description = "Réponse libre, 4 000 caractères maximum", example = "Migration du back-office vers React")
            @Size(max = 4000) String text,
            @Schema(description = "Note entière de 1 à 5", example = "4")
            @Min(1) @Max(5) Integer rating) {
    }

    @Schema(description = "Auto-évaluation (brouillon)")
    public record SelfReviewRequest(
            @Schema(description = "Réponses (les questions omises restent vides)")
            @NotNull @Size(max = 20) List<@Valid AnswerRequest> answers) {
    }

    @Schema(description = "Objectif pour l'année suivante")
    public record ObjectiveRequest(
            @Schema(description = "Intitulé, 200 caractères maximum", example = "Mentorer un développeur junior")
            @NotBlank @Size(max = 200) String title,
            @Schema(description = "Détail, 1 000 caractères maximum", example = "Point hebdomadaire et revue de code")
            @Size(max = 1000) String description) {
    }

    @Schema(description = "Évaluation du manager (brouillon) : réponses et 1 à 5 objectifs (exigés à la soumission)")
    public record ManagerReviewRequest(
            @Schema(description = "Réponses du manager")
            @NotNull @Size(max = 20) List<@Valid AnswerRequest> answers,
            @Schema(description = "Objectifs pour l'année suivante, 5 maximum")
            @NotNull @Size(max = 5) List<@Valid ObjectiveRequest> objectives) {
    }

    @Schema(description = "Signature ; le salarié peut ajouter un commentaire final")
    public record SignRequest(
            @Schema(description = "Commentaire final du salarié (ignoré pour le manager), 2 000 caractères maximum", example = "Merci pour cet échange constructif")
            @Size(max = 2000) String finalComment) {
    }

    @Schema(description = "Réouverture après la date limite")
    public record ReopenRequest(
            @Schema(description = "Nombre de jours de saisie accordés, de 1 à 30 (7 par défaut)", example = "7")
            @Min(1) @Max(30) Integer days) {
    }

    @Schema(description = "AppraisalQuestion")
    public record QuestionDto(UUID id, int position, String question, QuestionType type) {
    }

    @Schema(description = "Campagne")
    public record CampaignDto(UUID id, int year, LocalDate deadline, List<QuestionDto> template, long appraisals) {
    }

    @Schema(description = "Avancement d'une campagne : nombre d'entretiens par étape et en retard")
    public record ProgressDto(UUID campaignId, int year, LocalDate deadline, long total, Map<Step, Long> byStep, long late) {
    }

    @Schema(description = "Réponse")
    public record AnswerDto(UUID questionId, String text, Integer rating) {
    }

    @Schema(description = "Objectif")
    public record ObjectiveDto(int position, String title, String description) {
    }

    @Schema(description = """
            Entretien. `selfAnswers` : null pour le manager tant que l'auto-évaluation n'est pas soumise ; \
            `managerAnswers` et `objectives` visibles du salarié après la soumission du manager. \
            `editable` : l'utilisateur courant peut saisir à l'étape actuelle (date limite ou réouverture comprises).""")
    public record AppraisalDto(UUID id, UUID campaignId, int year, LocalDate deadline, UUID employeeId, String employeeName,
                               UUID managerId, String managerName, Step step, String role, boolean editable, boolean deadlinePassed,
                               Instant reopenedUntil, List<QuestionDto> questions, List<AnswerDto> selfAnswers,
                               List<AnswerDto> managerAnswers, List<ObjectiveDto> objectives, String finalComment,
                               Instant employeeSignedAt, Instant managerSignedAt) {
    }

    @Schema(description = "Entretien dans une liste")
    public record AppraisalSummary(UUID id, int year, UUID employeeId, String employeeName, Step step, boolean late) {
    }
}
