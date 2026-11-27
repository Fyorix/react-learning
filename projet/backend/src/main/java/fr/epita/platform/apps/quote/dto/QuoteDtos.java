package fr.epita.platform.apps.quote.dto;

import fr.epita.platform.apps.quote.entity.QuoteStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class QuoteDtos {

    private QuoteDtos() {
    }

    @Schema(description = "Ajout ou modification d'un produit du catalogue (ADMIN)")
    public record ProductRequest(
            @Schema(description = "Référence unique (insensible à la casse), 30 caractères maximum", example = "JEU-0042")
            @NotBlank @Size(max = 30) String reference,
            @Schema(description = "Nom du produit, 150 caractères maximum", example = "Les Aventuriers du Rail")
            @NotBlank @Size(max = 150) String name,
            @Schema(description = "Catégorie libre, 60 caractères maximum", example = "Jeux de société")
            @NotBlank @Size(max = 60) String category,
            @Schema(description = "Prix unitaire hors taxes, en euros, 2 décimales maximum", example = "39.90")
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @Schema(description = "Taux de TVA en % : 0, 5.5, 10 ou 20", example = "20", allowableValues = {"0", "5.5", "10", "20"})
            @NotNull BigDecimal vatRate) {
    }

    @Schema(description = "Produit du catalogue")
    public record ProductDto(UUID id, String reference, String name, String category, BigDecimal unitPrice,
                             BigDecimal vatRate, boolean archived) {
    }

    @Schema(description = "Ligne de devis")
    public record LineRequest(
            @Schema(description = "Produit du catalogue (non archivé)", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID productId,
            @Schema(description = "Quantité, de 1 à 9999", example = "3")
            @NotNull @Min(1) @Max(9999) Integer quantity,
            @Schema(description = "Remise en %, de 0 à 100, 2 décimales maximum (0 par défaut)", example = "10")
            @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discount) {
    }

    @Schema(description = "Création ou modification d'un devis en brouillon (SALES)")
    public record QuoteRequest(
            @Schema(description = "Client destinataire (utilisateur de la plateforme, différent du commercial)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID clientId,
            @Schema(description = "Objet du devis, 150 caractères maximum", example = "Jeux pour la ludothèque du CE")
            @NotBlank @Size(max = 150) String title,
            @Schema(description = "Lignes du devis (50 maximum ; une liste vide est permise en brouillon)")
            @NotNull @Size(max = 50) List<@Valid LineRequest> lines) {
    }

    @Schema(description = "Envoi d'un devis au client")
    public record SendRequest(
            @Schema(description = "Date de fin de validité (AAAA-MM-JJ), dans le futur", example = "2026-12-31")
            @NotNull LocalDate validUntil) {
    }

    @Schema(description = "Refus d'un devis par le client")
    public record RefuseRequest(
            @Schema(description = "Motif du refus, obligatoire, 1000 caractères maximum", example = "Budget insuffisant cette année")
            @NotBlank @Size(max = 1000) String comment) {
    }

    @Schema(description = "Ligne de devis avec son total")
    public record LineDto(UUID id, UUID productId, String reference, String label, int quantity, BigDecimal unitPrice,
                          BigDecimal discount, BigDecimal vatRate, BigDecimal totalExclTax, boolean frozen) {
    }

    @Schema(description = "Montant de TVA pour un taux")
    public record VatDto(BigDecimal rate, BigDecimal base, BigDecimal amount) {
    }

    @Schema(description = "Totaux d'un devis")
    public record TotalsDto(BigDecimal totalExclTax, List<VatDto> vat, BigDecimal totalVat, BigDecimal totalInclTax) {
    }

    @Schema(description = "Devis")
    public record QuoteDto(UUID id, String number, String title, QuoteStatus status, UUID salesId, String salesName,
                           UUID clientId, String clientName, LocalDate validUntil, Instant sentAt, Instant decidedAt,
                           String refusalComment, List<LineDto> lines, TotalsDto totals, boolean canEdit,
                           boolean canDecide, Instant createdAt, Instant updatedAt) {
    }

    @Schema(description = "Devis dans une liste")
    public record QuoteSummaryDto(UUID id, String number, String title, QuoteStatus status, String salesName,
                                  String clientName, LocalDate validUntil, BigDecimal totalInclTax, Instant createdAt) {
    }

    @Schema(description = "Statistiques commerciales du mois")
    public record QuoteStatsDto(String month, Map<QuoteStatus, Long> byStatus, BigDecimal acceptedAmountExclTax,
                                long decided, Integer conversionRate) {
    }

    @Schema(description = "Client possible pour un devis")
    public record ClientDto(UUID id, String displayName) {
    }
}
