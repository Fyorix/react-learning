package fr.epita.platform.apps.onboarding.dto;

import fr.epita.platform.apps.onboarding.entity.TaskOwner;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class OnboardingDtos {

    private OnboardingDtos() {
    }

    private static final String OWNER = "Responsable : NEWCOMER = l'arrivant, MANAGER = son manager, BUDDY = son parrain, IT = service informatique, HR = ressources humaines (IT et HR sont cochées par un ADMIN)";

    @Schema(description = "Tâche d'un modèle de parcours")
    public record TemplateTaskRequest(
            @Schema(description = "Intitulé, 150 caractères maximum", example = "Récupérer son badge et son ordinateur")
            @NotBlank @Size(max = 150) String title,
            @Schema(description = "Détail facultatif, 1000 caractères maximum", example = "Au bureau 104, avec une pièce d'identité")
            @Size(max = 1000) String description,
            @Schema(description = OWNER, example = "NEWCOMER")
            @NotNull TaskOwner owner,
            @Schema(description = "Échéance en jours par rapport à la date d'arrivée (J0) ; négatif = avant l'arrivée ; de -30 à 180", example = "1")
            @NotNull @Min(-30) @Max(180) Integer dueOffsetDays,
            @Schema(description = "true : tâche obligatoire pour terminer le parcours", example = "true")
            @NotNull Boolean mandatory) {
    }

    @Schema(description = "Création ou modification d'un modèle de parcours (ADMIN)")
    public record TemplateRequest(
            @Schema(description = "Nom unique du modèle, 100 caractères maximum", example = "Développeur")
            @NotBlank @Size(max = 100) String name,
            @Schema(description = "Tâches : 1 à 50")
            @NotNull @Size(min = 1, max = 50) List<@Valid TemplateTaskRequest> tasks) {
    }

    @Schema(description = "Lancement d'un parcours (ADMIN)")
    public record JourneyRequest(
            @Schema(description = "Modèle à copier", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID templateId,
            @Schema(description = "Nouvel arrivant (un seul parcours actif par arrivant)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID newcomerId,
            @Schema(description = "Manager de l'arrivant, différent de lui", example = "0fee6165-1706-4ef5-9936-e2309e2f481c")
            @NotNull UUID managerId,
            @Schema(description = "Parrain, différent de l'arrivant et du manager", example = "68c4e6eb-9f0c-4281-842d-c7eef3364bd8")
            @NotNull UUID buddyId,
            @Schema(description = "Date d'arrivée J0 (AAAA-MM-JJ)", example = "2027-01-04")
            @NotNull LocalDate startDate) {
    }

    @Schema(description = "Tâche d'un modèle")
    public record TemplateTaskDto(UUID id, String title, String description, TaskOwner owner, int dueOffsetDays, boolean mandatory) {
    }

    @Schema(description = "Modèle de parcours")
    public record TemplateDto(UUID id, String name, List<TemplateTaskDto> tasks) {
    }

    @Schema(description = "Tâche d'un parcours, avec son échéance calculée")
    public record TaskDto(UUID id, String title, String description, TaskOwner owner, LocalDate dueDate, boolean mandatory,
                          boolean done, Instant doneAt, String doneByName, boolean overdue, boolean canToggle) {
    }

    @Schema(description = "Parcours d'intégration")
    public record JourneyDto(UUID id, String templateName, UUID newcomerId, String newcomerName, UUID managerId,
                             String managerName, UUID buddyId, String buddyName, LocalDate startDate, String status,
                             int done, int total, int progress, int overdue, List<TaskDto> tasks) {
    }
}
