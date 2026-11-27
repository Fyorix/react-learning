package fr.epita.platform.apps.quiz.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class QuizDtos {

    private QuizDtos() {
    }

    @Schema(description = "Réponse proposée")
    public record AnswerRequest(
            @Schema(description = "Libellé de la réponse, 300 caractères maximum", example = "useState")
            @NotBlank @Size(max = 300) String label,
            @Schema(description = "true si cette réponse est juste (au moins une bonne réponse par question)", example = "true")
            boolean correct) {
    }

    @Schema(description = "Question à choix multiples")
    public record QuestionRequest(
            @Schema(description = "Énoncé, 500 caractères maximum", example = "Quel hook gère un état local ?")
            @NotBlank @Size(max = 500) String label,
            @Schema(description = "Réponses proposées : au moins 2, dont au moins 1 bonne")
            @NotNull @Size(min = 2, message = "Chaque question doit avoir au moins 2 réponses")
                                  List<@Valid AnswerRequest> answers) {
    }

    @Schema(description = "Création ou modification d'un quiz (modification impossible une fois publié)")
    public record QuizRequest(
            @Schema(description = "Titre, 150 caractères maximum", example = "Les bases de React")
            @NotBlank @Size(max = 150) String title,
            @Schema(description = "Description facultative, 1000 caractères maximum", example = "Dix questions pour réviser les hooks")
            @Size(max = 1000) String description,
            @Schema(description = "Questions : au moins 1")
            @NotNull @Size(min = 1, message = "Un quiz doit avoir au moins une question")
                              List<@Valid QuestionRequest> questions) {
    }

    @Schema(description = "Réponse à une question")
    public record SubmittedAnswer(
            @Schema(description = "Identifiant de la question", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID questionId,
            @Schema(description = "Réponses cochées (liste vide autorisée : la question compte alors comme fausse)", example = "[\"9b76d11e-ec02-474c-8ca9-8886c4bfbb7d\"]")
            @NotNull List<UUID> answerIds) {
    }

    @Schema(description = "Tentative : une entrée par question du quiz")
    public record AttemptRequest(
            @Schema(description = "Réponses, une par question")
            @NotEmpty List<@Valid SubmittedAnswer> answers) {
    }

    /** {@code correct} n'est renseigné que pour un ADMIN (jamais envoyé à un USER). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AnswerDto(UUID id, String label, Boolean correct) {
    }

    public record QuestionDto(UUID id, String label, boolean multipleAnswers, List<AnswerDto> answers) {
    }

    public record QuizDto(UUID id, String title, String description, boolean published, Instant publishedAt,
                          int questionCount, long attemptCount, boolean attempted, List<QuestionDto> questions) {
    }

    public record QuizSummaryDto(UUID id, String title, String description, boolean published, int questionCount,
                                 long attemptCount, boolean attempted, Integer myScore, Instant createdAt) {
    }

    public record CorrectionDto(UUID questionId, String label, List<UUID> correctAnswerIds,
                                List<UUID> selectedAnswerIds, boolean correct) {
    }

    public record AttemptResultDto(UUID attemptId, UUID quizId, String quizTitle, int score, int total,
                                   Instant submittedAt, List<CorrectionDto> corrections) {
    }

    public record AttemptDto(UUID attemptId, UUID quizId, String quizTitle, int score, int total, Instant submittedAt) {
    }

    public record LeaderboardEntry(int rank, UUID userId, String displayName, int score, int total,
                                   int quizzesTaken, Instant submittedAt) {
    }
}
