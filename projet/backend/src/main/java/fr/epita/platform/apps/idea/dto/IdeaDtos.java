package fr.epita.platform.apps.idea.dto;

import fr.epita.platform.apps.idea.entity.IdeaCategory;
import fr.epita.platform.apps.idea.entity.IdeaStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class IdeaDtos {

    private IdeaDtos() {
    }

    @Schema(description = "Soumission ou modification d'une idée (modification possible seulement au statut NEW)")
    public record IdeaRequest(
            @Schema(description = "Titre, 5 à 120 caractères", example = "Installer des fontaines à eau")
            @NotBlank @Size(min = 5, max = 120) String title,
            @Schema(description = "Description, 2000 caractères maximum", example = "Remplacer les bouteilles plastiques par des fontaines à chaque étage.")
            @NotBlank @Size(max = 2000) String description,
            @Schema(description = "Catégorie (voir GET /api/ideas/categories)", example = "WELLBEING")
            @NotNull IdeaCategory category) {
    }

    @Schema(description = "Changement de statut (ADMIN)")
    public record StatusRequest(
            @Schema(description = "Nouveau statut : NEW = nouvelle, UNDER_REVIEW = en cours d'étude, ACCEPTED = acceptée, REJECTED = refusée, DONE = réalisée. Transitions : NEW → UNDER_REVIEW → ACCEPTED / REJECTED, ACCEPTED → DONE", example = "UNDER_REVIEW")
            @NotNull IdeaStatus status,
            @Schema(description = "Commentaire facultatif de la décision, 500 caractères maximum", example = "À l'étude avec les services généraux")
            @Size(max = 500) String comment) {
    }

    @Schema(description = "Nouveau commentaire")
    public record CommentRequest(
            @Schema(description = "Texte du commentaire, 1000 caractères maximum", example = "Bonne idée, on pourrait aussi ajouter des gourdes.")
            @NotBlank @Size(max = 1000) String content) {
    }

    public record IdeaDto(UUID id, String title, String description, IdeaCategory category, String categoryLabel,
                          IdeaStatus status, String statusComment, UUID authorId, String authorName, int votes,
                          boolean hasVoted, boolean canEdit, long commentCount, Instant createdAt, Instant updatedAt) {
    }

    public record CommentDto(UUID id, UUID authorId, String authorName, String content, Instant createdAt) {
    }

    public record CategoryDto(IdeaCategory code, String label) {
    }
}
