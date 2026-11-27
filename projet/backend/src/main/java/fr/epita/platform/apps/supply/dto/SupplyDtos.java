package fr.epita.platform.apps.supply.dto;

import fr.epita.platform.apps.supply.entity.RequestStatus;
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
import java.util.UUID;

public final class SupplyDtos {

    private SupplyDtos() {
    }

    @Schema(description = "Article du catalogue (ADMIN). Le stock initial n'est pris en compte qu'à la création ; ensuite il évolue par réapprovisionnements et remises.")
    public record ItemRequest(
            @Schema(description = "Nom unique, 100 caractères maximum", example = "Stylo bille bleu")
            @NotBlank @Size(max = 100) String name,
            @Schema(description = "Catégorie, 50 caractères maximum", example = "Écriture")
            @NotBlank @Size(max = 50) String category,
            @Schema(description = "Unité, 30 caractères maximum", example = "pièce")
            @NotBlank @Size(max = 30) String unit,
            @Schema(description = "Stock initial (création uniquement)", example = "200")
            @Min(0) @Max(100000) int stock,
            @Schema(description = "Seuil d'alerte de réapprovisionnement", example = "50")
            @Min(0) @Max(100000) int reorderThreshold,
            @Schema(description = "Quota mensuel par personne (facultatif)", example = "5")
            @Min(1) @Max(1000) Integer monthlyQuota,
            @Schema(description = "Proposé au catalogue", example = "true")
            Boolean active) {
    }

    @Schema(description = "Ligne de demande")
    public record LineRequest(
            @Schema(description = "Article", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID itemId,
            @Schema(description = "Quantité, de 1 à 100", example = "2")
            @Min(1) @Max(100) int quantity) {
    }

    @Schema(description = "Demande de fournitures")
    public record SupplyRequestBody(
            @Schema(description = "Lignes (1 à 20) ; un même article répété est cumulé")
            @NotEmpty @Size(max = 20) List<@Valid LineRequest> lines) {
    }

    @Schema(description = "Refus d'une demande")
    public record RejectRequest(
            @Schema(description = "Motif, 300 caractères maximum", example = "Commande groupée prévue la semaine prochaine")
            @NotBlank @Size(max = 300) String reason) {
    }

    @Schema(description = "Réapprovisionnement")
    public record RestockRequest(
            @Schema(description = "Quantité ajoutée au stock, de 1 à 100 000", example = "100")
            @Min(1) @Max(100000) int quantity) {
    }

    @Schema(description = "Article ; `available` = stock − réservé ; `alert` = disponible sous le seuil")
    public record ItemDto(UUID id, String name, String category, String unit, int stock, int reserved, int available,
                          int reorderThreshold, Integer monthlyQuota, boolean active, boolean alert, Integer myRemainingQuota) {
    }

    @Schema(description = "Ligne d'une demande")
    public record LineDto(UUID itemId, String itemName, String unit, int quantity) {
    }

    @Schema(description = "Demande")
    public record RequestDto(UUID id, UUID userId, String userName, String teamName, RequestStatus status, String reason,
                             String decidedByName, Instant createdAt, Instant deliveredAt, List<LineDto> lines) {
    }

    @Schema(description = "Consommation (quantités remises) par article et par équipe — l'équipe est le manager du demandeur")
    public record ConsumptionDto(List<ConsumptionRow> byItem, List<ConsumptionRow> byTeam) {
    }

    @Schema(description = "Ligne de consommation")
    public record ConsumptionRow(String label, long quantity) {
    }
}
