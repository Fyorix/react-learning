package fr.epita.platform.apps.kanban.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class KanbanDtos {

    private KanbanDtos() {
    }

    @Schema(description = "Création ou modification d'un tableau")
    public record BoardRequest(
            @Schema(description = "Nom du tableau, 100 caractères maximum", example = "Sprint 42")
            @NotBlank @Size(max = 100) String name,
            @Schema(description = "Membres (hors propriétaire, toujours membre), 30 maximum", example = "[\"9b76d11e-ec02-474c-8ca9-8886c4bfbb7d\"]")
            @NotNull @Size(max = 30) List<UUID> memberIds) {
    }

    @Schema(description = "Création ou modification d'une colonne")
    public record ColumnRequest(
            @Schema(description = "Nom de la colonne, 60 caractères maximum", example = "En revue")
            @NotBlank @Size(max = 60) String name,
            @Schema(description = "Limite de cartes (WIP) de 1 à 50, ou null pour aucune limite ; jamais sous le nombre de cartes présentes", example = "3")
            @Min(1) @Max(50) Integer wipLimit,
            @Schema(description = "Position souhaitée (0 = première) ; à la fin si absente", example = "1")
            @Min(0) Integer position) {
    }

    @Schema(description = "Création ou modification d'une carte")
    public record CardRequest(
            @Schema(description = "Titre, 150 caractères maximum", example = "Écrire les tests du panier")
            @NotBlank @Size(max = 150) String title,
            @Schema(description = "Description facultative, 4000 caractères maximum", example = "Couvrir l'ajout, la suppression et le total.")
            @Size(max = 4000) String description,
            @Schema(description = "Assigné facultatif, obligatoirement membre du tableau", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            UUID assigneeId,
            @Schema(description = "Échéance facultative (AAAA-MM-JJ)", example = "2026-12-05")
            LocalDate dueDate,
            @Schema(description = "Étiquettes : 5 maximum, chacune de 1 à 20 caractères (lettres, chiffres, tirets)", example = "[\"front\", \"urgent\"]")
            @Size(max = 5) List<@NotBlank @Size(max = 20) @Pattern(regexp = "[\\p{L}0-9-]+") String> labels) {
    }

    @Schema(description = "Déplacement d'une carte (glisser-déposer)")
    public record MoveRequest(
            @Schema(description = "Colonne d'arrivée (du même tableau)", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID columnId,
            @Schema(description = "Position dans la colonne d'arrivée (0 = en haut) ; ramenée à la fin si trop grande", example = "0")
            @NotNull @Min(0) Integer position) {
    }

    @Schema(description = "Carte")
    public record CardDto(UUID id, UUID columnId, String title, String description, UUID assigneeId, String assigneeName,
                          LocalDate dueDate, List<String> labels, int position, boolean overdue) {
    }

    @Schema(description = "Colonne avec ses cartes ordonnées")
    public record ColumnDto(UUID id, String name, int position, Integer wipLimit, List<CardDto> cards) {
    }

    @Schema(description = "Membre d'un tableau")
    public record MemberDto(UUID id, String displayName, boolean owner) {
    }

    @Schema(description = "Tableau complet")
    public record BoardDto(UUID id, String name, UUID ownerId, List<MemberDto> members, List<ColumnDto> columns, boolean isOwner) {
    }

    @Schema(description = "Tableau dans une liste")
    public record BoardSummaryDto(UUID id, String name, String ownerName, int memberCount, long cardCount) {
    }

    @Schema(description = "Déplacement historisé")
    public record MoveDto(String fromColumn, String toColumn, String authorName, Instant at) {
    }

    @Schema(description = "Carte d'un de mes tableaux")
    public record MyCardDto(UUID boardId, String boardName, String columnName, CardDto card) {
    }
}
