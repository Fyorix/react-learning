package fr.epita.platform.apps.goals.dto;

import fr.epita.platform.apps.goals.entity.GoalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class GoalDtos {

    private GoalDtos() {
    }

    public enum ReviewDecision { VALIDATED, NOT_VALIDATED, REOPEN }

    /** Contrôles SMART : S (title, description), M (metric, unit, valeurs), T (dueDate) ; A et I dans le service. */
    @Schema(description = "Objectif SMART fixé par le manager à un seul salarié")
    public record GoalRequest(
            @Schema(description = "Salarié concerné (Individuel) : un seul, dont le demandeur est le manager", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID employeeId,
            @Schema(description = "Spécifique : intitulé, 5 à 120 caractères", example = "Réduire le temps de build")
            @NotBlank @Size(min = 5, max = 120, message = "Spécifique : le titre fait entre 5 et 120 caractères") String title,
            @Schema(description = "Spécifique : description détaillée, 2000 caractères maximum", example = "Passer le build CI sous les 5 minutes")
            @NotBlank(message = "Spécifique : la description est obligatoire") @Size(max = 2000) String description,
            @Schema(description = "Mesurable : libellé de l'indicateur, 100 caractères maximum", example = "Durée du build")
            @NotBlank(message = "Mesurable : l'indicateur est obligatoire") @Size(max = 100) String metric,
            @Schema(description = "Mesurable : unité, 30 caractères maximum", example = "minutes")
            @NotBlank(message = "Mesurable : l'unité est obligatoire") @Size(max = 30) String unit,
            @Schema(description = "Mesurable : valeur de départ", example = "12")
            @NotNull(message = "Mesurable : la valeur de départ est obligatoire") BigDecimal startValue,
            @Schema(description = "Mesurable / Atteignable : valeur cible, différente de `startValue`", example = "5")
            @NotNull(message = "Mesurable : la valeur cible est obligatoire") BigDecimal targetValue,
            @Schema(description = "Temporel : échéance (AAAA-MM-JJ), dans le futur et à 12 mois maximum", example = "2027-03-31")
            @NotNull(message = "Temporel : l'échéance est obligatoire") LocalDate dueDate) {
    }

    @Schema(description = "Avancement saisi par le salarié")
    public record ProgressRequest(
            @Schema(description = "Valeur actuelle de l'indicateur", example = "9")
            @NotNull BigDecimal value,
            @Schema(description = "Commentaire facultatif, 500 caractères maximum", example = "Cache des dépendances activé")
            @Size(max = 500) String comment) {
    }

    @Schema(description = "Décision du manager sur un objectif soumis")
    public record ReviewRequest(
            @Schema(description = "VALIDATED = atteint, NOT_VALIDATED = non atteint (commentaire obligatoire), REOPEN = renvoyé en IN_PROGRESS", example = "VALIDATED")
            @NotNull ReviewDecision decision,
            @Schema(description = "Commentaire, 1000 caractères maximum ; obligatoire si NOT_VALIDATED", example = "Objectif atteint avec un mois d'avance")
            @Size(max = 1000) String comment) {
    }

    @Schema(description = "Affectation d'un manager")
    public record AssignManagerRequest(
            @Schema(description = "Manager à affecter (différent du salarié, sans créer de cycle)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID managerId) {
    }

    public record ProgressDto(UUID id, BigDecimal value, String comment, Instant createdAt) {
    }

    public record GoalDto(UUID id, UUID employeeId, String employeeName, UUID managerId, String managerName,
                          String title, String description, String metric, String unit, BigDecimal startValue,
                          BigDecimal targetValue, BigDecimal currentValue, int progress, LocalDate dueDate,
                          GoalStatus status, String reviewComment, Instant submittedAt, boolean hasProgress,
                          boolean canEdit, boolean canRecordProgress, boolean canReview,
                          List<ProgressDto> history) {
    }

    public record EmployeeStatsDto(UUID employeeId, String employeeName, long total, long inProgress, long submitted,
                                   long validated, long notValidated, int averageProgress, Integer validationRate) {
    }

    public record ReportDto(UUID employeeId, String employeeName, UUID managerId, String managerName) {
    }
}
