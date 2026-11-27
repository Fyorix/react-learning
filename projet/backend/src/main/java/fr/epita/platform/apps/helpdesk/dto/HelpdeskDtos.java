package fr.epita.platform.apps.helpdesk.dto;

import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.Category;
import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.Priority;
import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HelpdeskDtos {

    private HelpdeskDtos() {
    }

    @Schema(description = "Ouverture d'un ticket")
    public record TicketRequest(
            @Schema(description = "Titre, 150 caractères maximum", example = "Impossible d'imprimer depuis le 2e étage")
            @NotBlank @Size(max = 150) String title,
            @Schema(description = "Description du problème, 4000 caractères maximum", example = "L'imprimante affiche « hors ligne » depuis ce matin.")
            @NotBlank @Size(max = 4000) String description,
            @Schema(description = "Catégorie : HARDWARE = matériel, SOFTWARE = logiciel, NETWORK = réseau, ACCESS = droits d'accès, OTHER = autre", example = "HARDWARE")
            @NotNull Category category,
            @Schema(description = "Priorité, qui fixe le délai de résolution attendu : CRITICAL = 4 h, HIGH = 1 jour, NORMAL = 3 jours, LOW = 5 jours ouvrés", example = "NORMAL")
            @NotNull Priority priority) {
    }

    @Schema(description = "Commentaire sur un ticket")
    public record CommentRequest(
            @Schema(description = "Texte, 4000 caractères maximum", example = "Je passe vérifier le câble réseau à 14 h.")
            @NotBlank @Size(max = 4000) String content,
            @Schema(description = "true : commentaire interne, visible des seuls techniciens (réservé aux TECHNICIAN) ; false par défaut", example = "false")
            Boolean internal) {
    }

    @Schema(description = "Assignation d'un ticket")
    public record AssignRequest(
            @Schema(description = "Technicien assigné (rôle TECHNICIAN ou ADMIN). Un technicien ne peut s'assigner qu'à lui-même.", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID technicianId) {
    }

    @Schema(description = "Changement de statut")
    public record StatusRequest(
            @Schema(description = "Nouveau statut. Transitions : OPEN → IN_PROGRESS → RESOLVED → CLOSED", example = "RESOLVED")
            @NotNull TicketStatus status,
            @Schema(description = "Commentaire facultatif ajouté au fil (public), 4000 caractères maximum", example = "Toner remplacé.")
            @Size(max = 4000) String comment) {
    }

    @Schema(description = "Réouverture par le demandeur")
    public record ReopenRequest(
            @Schema(description = "Raison de la réouverture, obligatoire, 4000 caractères maximum", example = "Le problème est revenu ce matin.")
            @NotBlank @Size(max = 4000) String reason) {
    }

    @Schema(description = "Commentaire")
    public record CommentDto(UUID id, UUID authorId, String authorName, String content, boolean internal, Instant createdAt) {
    }

    @Schema(description = "Ticket")
    public record TicketDto(UUID id, String title, String description, Category category, Priority priority,
                            TicketStatus status, UUID requesterId, String requesterName, UUID assigneeId,
                            String assigneeName, Instant dueAt, boolean overdue, Instant resolvedAt, Instant closedAt,
                            boolean canReopen, Instant createdAt, Instant updatedAt, List<CommentDto> comments) {
    }

    @Schema(description = "Statistiques du support")
    public record StatsDto(Map<TicketStatus, Long> byStatus, Map<String, Long> openByTechnician, long overdue,
                           Double averageResolutionHours) {
    }
}
