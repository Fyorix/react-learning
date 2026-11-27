package fr.epita.platform.apps.access.dto;

import fr.epita.platform.apps.access.entity.AccessLevel;
import fr.epita.platform.apps.access.entity.AccessRequestStatus;
import fr.epita.platform.apps.access.entity.AuditAction;
import fr.epita.platform.apps.access.entity.GrantStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AccessDtos {

    private AccessDtos() {
    }

    @Schema(description = "Outil interne (ADMIN)")
    public record ToolRequest(
            @Schema(description = "Nom unique, 80 caractères maximum", example = "Jira")
            @NotBlank @Size(max = 80) String name,
            @Schema(description = "Propriétaire : valide les demandes et revoit les accès", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID ownerId,
            @Schema(description = "Niveaux proposés parmi READ, WRITE, ADMIN", example = "[\"READ\", \"WRITE\"]")
            @NotEmpty @Size(max = 3) List<AccessLevel> levels) {
    }

    @Schema(description = "Demande d'accès")
    public record AccessRequestBody(
            @Schema(description = "Outil", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID toolId,
            @Schema(description = "Niveau proposé par l'outil", example = "WRITE")
            @NotNull AccessLevel level,
            @Schema(description = "Justification, 1 000 caractères maximum", example = "Je rejoins l'équipe support")
            @NotBlank @Size(max = 1000) String justification,
            @Schema(description = "Durée en jours, de 1 à 365 (90 maximum pour ADMIN)", example = "90")
            @Min(1) @Max(365) int durationDays) {
    }

    @Schema(description = "Refus (motif obligatoire)")
    public record RejectRequest(
            @Schema(description = "Motif, 500 caractères maximum", example = "Accès en lecture suffisant pour ce besoin")
            @NotBlank @Size(max = 500) String reason) {
    }

    @Schema(description = "Outil")
    public record ToolDto(UUID id, String name, UUID ownerId, String ownerName, List<AccessLevel> levels, AccessLevel myLevel) {
    }

    @Schema(description = "Demande ; `canApprove` : l'utilisateur courant peut valider l'étape en cours")
    public record AccessRequestDto(UUID id, UUID toolId, String toolName, UUID userId, String userName, AccessLevel level, String justification,
                                   int durationDays, AccessRequestStatus status, String managerName, String ownerName, String reason,
                                   String decidedByName, Instant createdAt, boolean canApprove) {
    }

    @Schema(description = "Accès ; `reviewDue` : non confirmé depuis 180 jours")
    public record GrantDto(UUID id, UUID toolId, String toolName, UUID userId, String userName, AccessLevel level, GrantStatus status,
                           Instant grantedAt, Instant expiresAt, Instant lastConfirmedAt, boolean reviewDue) {
    }

    @Schema(description = "Entrée du journal d'audit")
    public record AuditDto(UUID id, AuditAction action, String toolName, String userName, AccessLevel level, String actorName, String detail, Instant at) {
    }
}
