package fr.epita.platform.apps.grouporder.dto;

import fr.epita.platform.apps.grouporder.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GroupOrderDtos {

    private GroupOrderDtos() {
    }

    // ---------- requêtes ----------

    @Schema(description = "Nouveau fournisseur (ADMIN)")
    public record SupplierRequest(
            @Schema(description = "Nom unique, 80 caractères maximum", example = "Pizzeria Napoli")
            @NotBlank @Size(max = 80) String name,
            @Schema(description = "Minimum de commande hors livraison, en euros", example = "30.00")
            @NotNull @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal minimumOrder,
            @Schema(description = "Frais de livraison, en euros, répartis entre les participants", example = "4.50")
            @NotNull @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal deliveryFee) {
    }

    @Schema(description = "Option d'un article de la carte")
    public record OptionRequest(
            @Schema(description = "Option existante à conserver ; absent pour en créer une", example = "5b0d3c1e-7a44-4d0a-9a39-7c59e0a2f1aa")
            UUID id,
            @Schema(description = "Libellé, 60 caractères maximum", example = "Supplément burrata")
            @NotBlank @Size(max = 60) String label,
            @Schema(description = "Supplément en euros", example = "2.50")
            @NotNull @DecimalMin("0.00") @Digits(integer = 6, fraction = 2) BigDecimal extraPrice) {
    }

    @Schema(description = "Article de la carte")
    public record MenuItemRequest(
            @Schema(description = "Article existant à conserver ; absent pour en créer un", example = "2c7c1d8e-6f5a-4a3b-8e21-0b9f6d1c2e3f")
            UUID id,
            @Schema(description = "Nom, 80 caractères maximum", example = "Margherita")
            @NotBlank @Size(max = 80) String name,
            @Schema(description = "Prix en euros", example = "9.50")
            @NotNull @DecimalMin("0.01") @Digits(integer = 6, fraction = 2) BigDecimal price,
            @Schema(description = "Disponible à la commande", example = "true")
            boolean available,
            @Schema(description = "Options (10 maximum)")
            @Size(max = 10) List<@Valid OptionRequest> options) {
    }

    @Schema(description = "Nouvelle carte : remplace la précédente, les articles absents sont archivés")
    public record MenuRequest(
            @Schema(description = "Articles dans l'ordre d'affichage, 100 maximum")
            @NotNull @Size(max = 100) List<@Valid MenuItemRequest> items) {
    }

    @Schema(description = "Ouverture d'une commande groupée")
    public record OrderRequest(
            @Schema(description = "Fournisseur", example = "8d7f6e5d-4c3b-2a19-8070-6f5e4d3c2b1a")
            @NotNull UUID supplierId,
            @Schema(description = "Heure limite pour ajouter ses articles (ISO-8601, dans le futur)", example = "2026-12-03T11:30:00Z")
            @NotNull Instant deadline,
            @Schema(description = "Note pour les participants, 500 caractères maximum", example = "Livraison en salle Lovelace")
            @Size(max = 500) String note) {
    }

    @Schema(description = "Une ligne de mon panier")
    public record LineRequest(
            @Schema(description = "Article de la carte du fournisseur", example = "2c7c1d8e-6f5a-4a3b-8e21-0b9f6d1c2e3f")
            @NotNull UUID itemId,
            @Schema(description = "Options choisies parmi celles de l'article")
            List<UUID> optionIds,
            @Schema(description = "Quantité, de 1 à 20", example = "1")
            @Min(1) @Max(20) int quantity) {
    }

    @Schema(description = "Mon panier complet (remplace le précédent ; vide pour me retirer)")
    public record LinesRequest(
            @Schema(description = "Lignes, 20 maximum")
            @NotNull @Size(max = 20) List<@Valid LineRequest> lines) {
    }

    @Schema(description = "Paiement d'un participant")
    public record PaymentRequest(
            @Schema(description = "A payé sa part à l'organisateur", example = "true")
            @NotNull Boolean paid) {
    }

    // ---------- réponses ----------

    @Schema(description = "Option")
    public record OptionDto(UUID id, String label, BigDecimal extraPrice) {
    }

    @Schema(description = "Article de la carte")
    public record ItemDto(UUID id, String name, BigDecimal price, boolean available, List<OptionDto> options) {
    }

    @Schema(description = "Fournisseur et sa carte")
    public record SupplierDto(UUID id, String name, BigDecimal minimumOrder, BigDecimal deliveryFee, List<ItemDto> items) {
    }

    @Schema(description = "Commande dans une liste")
    public record OrderSummaryDto(UUID id, String supplierName, UUID organizerId, String organizerName, Instant deadline,
                                  OrderStatus status, String note, int participants, BigDecimal total) {
    }

    @Schema(description = "Ligne (prix figé à l'ajout)")
    public record LineDto(UUID itemId, String label, List<UUID> optionIds, BigDecimal unitPrice, int quantity, BigDecimal amount) {
    }

    @Schema(description = "Ce que doit une personne : ses lignes + sa part de livraison ; `paid` null tant que la commande est ouverte")
    public record PersonDto(UUID userId, String name, List<LineDto> lines, BigDecimal linesTotal, BigDecimal feeShare,
                            BigDecimal total, Boolean paid) {
    }

    @Schema(description = "Détail d'une commande")
    public record OrderDto(UUID id, UUID supplierId, String supplierName, BigDecimal minimumOrder, BigDecimal deliveryFee,
                           UUID organizerId, String organizerName, Instant deadline, String note, OrderStatus status,
                           boolean editable, boolean organizer, BigDecimal subtotal, BigDecimal total, boolean minimumReached,
                           List<PersonDto> persons, List<LineDto> mine) {
    }

    @Schema(description = "Ligne du bon de commande : quantité totale par article et options")
    public record SummaryRow(String label, int quantity, BigDecimal amount) {
    }

    @Schema(description = "Bon de commande consolidé à transmettre au fournisseur")
    public record SummaryDto(UUID orderId, String supplierName, List<SummaryRow> rows, BigDecimal subtotal,
                             BigDecimal deliveryFee, BigDecimal total) {
    }
}
