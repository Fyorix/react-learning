package fr.epita.platform.apps.recruitment.dto;

import fr.epita.platform.apps.recruitment.entity.JobStatus;
import fr.epita.platform.apps.recruitment.entity.Recommendation;
import fr.epita.platform.apps.recruitment.entity.Stage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RecruitmentDtos {

    private RecruitmentDtos() {
    }

    @Schema(description = "Poste (RECRUITER)")
    public record JobRequest(
            @Schema(description = "Intitulé, 120 caractères maximum", example = "Développeur·se React confirmé·e")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Équipe, 80 caractères maximum", example = "Produit")
            @NotBlank @Size(max = 80) String team,
            @Schema(description = "Description, 4 000 caractères maximum", example = "Vous rejoindrez l'équipe front…")
            @NotBlank @Size(max = 4000) String description,
            @Schema(description = "Lieu, 80 caractères maximum", example = "Paris (hybride)")
            @NotBlank @Size(max = 80) String location,
            @Schema(description = "Statut : OPEN, ON_HOLD, FILLED ou CLOSED (OPEN par défaut)", example = "OPEN")
            JobStatus status) {
    }

    @Schema(description = "Candidat (partie `data` du multipart, le CV PDF dans la partie `cv`)")
    public record CandidateRequest(
            @Schema(description = "Prénom, 60 caractères maximum", example = "Léa")
            @NotBlank @Size(max = 60) String firstName,
            @Schema(description = "Nom, 60 caractères maximum", example = "Moreau")
            @NotBlank @Size(max = 60) String lastName,
            @Schema(description = "E-mail ; une candidature par e-mail et par poste", example = "lea.moreau@example.com")
            @NotBlank @Email @Size(max = 160) String email,
            @Schema(description = "Téléphone (facultatif)", example = "+33 6 12 34 56 78")
            @Pattern(regexp = "[0-9 +().-]{6,30}") String phone) {
    }

    @Schema(description = "Changement d'étape")
    public record MoveRequest(
            @Schema(description = "Étape cible, jamais antérieure à l'étape actuelle", example = "HR_INTERVIEW")
            @NotNull Stage stage,
            @Schema(description = "Motif : obligatoire pour REJECTED et pour sauter une étape", example = "Profil très senior, entretien RH déjà fait par le manager")
            @Size(max = 500) String reason,
            @Schema(description = "Intervenants chargés d'évaluer (remplace la liste actuelle si fournie)")
            @Size(max = 10) List<UUID> interviewerIds) {
    }

    @Schema(description = "Évaluation d'entretien")
    public record EvaluationRequest(
            @Schema(description = "Étape évaluée : l'étape actuelle de la candidature", example = "TECH_INTERVIEW")
            @NotNull Stage stage,
            @Schema(description = "Note de 1 (insuffisant) à 4 (excellent)", example = "3")
            @Min(1) @Max(4) int rating,
            @Schema(description = "YES (favorable) ou NO", example = "YES")
            @NotNull Recommendation recommendation,
            @Schema(description = "Commentaire, 2 000 caractères maximum", example = "Bonne maîtrise des hooks, à l'aise en revue de code")
            @NotBlank @Size(max = 2000) String comment) {
    }

    @Schema(description = "Poste")
    public record JobDto(UUID id, String title, String team, String description, String location, JobStatus status, long applications, Instant createdAt) {
    }

    @Schema(description = "Candidature dans le pipeline")
    public record ApplicationSummary(UUID id, String candidateName, String email, Stage stage, Instant stageSince, int evaluations, int yes) {
    }

    @Schema(description = "Colonne du pipeline")
    public record StageColumn(Stage stage, List<ApplicationSummary> applications) {
    }

    @Schema(description = "Pipeline d'un poste : candidatures groupées par étape")
    public record PipelineDto(JobDto job, List<StageColumn> columns) {
    }

    @Schema(description = "Passage d'étape")
    public record StageEventDto(Stage fromStage, Stage toStage, String reason, String movedByName, Instant at) {
    }

    @Schema(description = "Évaluation")
    public record EvaluationDto(UUID id, UUID evaluatorId, String evaluatorName, Stage stage, int rating, Recommendation recommendation, String comment, Instant at) {
    }

    @Schema(description = "Intervenant")
    public record InterviewerDto(UUID userId, String name) {
    }

    @Schema(description = "Détail d'une candidature")
    public record ApplicationDto(UUID id, UUID jobId, String jobTitle, String firstName, String lastName, String email, String phone,
                                 String cvName, Stage stage, Instant stageSince, Instant appliedAt, List<InterviewerDto> interviewers,
                                 List<StageEventDto> history, List<EvaluationDto> evaluations, boolean canEvaluate) {
    }

    @Schema(description = "Évaluation à faire")
    public record TodoDto(UUID applicationId, String candidateName, String jobTitle, Stage stage, Instant stageSince) {
    }

    @Schema(description = "Statistiques : délai moyen candidature → embauche (jours) et taux de passage par étape (% des candidatures ayant atteint l'étape qui vont plus loin)")
    public record StatsDto(Double averageDaysToHire, long hires, Map<Stage, Integer> passRate, Map<Stage, Long> reached) {
    }
}
