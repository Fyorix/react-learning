package fr.epita.platform.apps.invoice.dto;

import fr.epita.platform.apps.invoice.entity.InvoiceStatus;
import fr.epita.platform.apps.invoice.entity.PaymentMethod;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class InvoiceDtos {

    private InvoiceDtos() {
    }

    @Schema(description = "Ligne de facture")
    public record LineRequest(
            @Schema(description = "Désignation, 200 caractères maximum", example = "Formation React — 2 jours")
            @NotBlank @Size(max = 200) String label,
            @Schema(description = "Quantité (2 décimales)", example = "2")
            @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal quantity,
            @Schema(description = "Prix unitaire HT en euros", example = "650.00")
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,
            @Schema(description = "Taux de TVA en %, de 0 à 100", example = "20")
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal vatRate) {
    }

    @Schema(description = "Facture en brouillon")
    public record InvoiceRequest(
            @Schema(description = "Client (utilisateur de la plateforme)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID clientId,
            @Schema(description = "Lignes (1 à 50)")
            @NotEmpty @Size(max = 50) List<@Valid LineRequest> lines,
            @Schema(description = "Date d'échéance", example = "2026-12-31")
            @NotNull LocalDate dueDate) {
    }

    @Schema(description = "Paiement reçu")
    public record PaymentRequest(
            @Schema(description = "Date du paiement, pas dans le futur", example = "2026-11-20")
            @NotNull LocalDate date,
            @Schema(description = "Montant en euros (partiel autorisé)", example = "500.00")
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Schema(description = "TRANSFER, CARD, CHECK ou CASH", example = "TRANSFER")
            @NotNull PaymentMethod method) {
    }

    @Schema(description = "Avoir sur une facture émise")
    public record CreditRequest(
            @Schema(description = "Montant TTC ≤ reste dû", example = "100.00")
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Schema(description = "Motif, 500 caractères maximum", example = "Geste commercial : demi-journée annulée")
            @NotBlank @Size(max = 500) String reason) {
    }

    @Schema(description = "Relance envoyée")
    public record ReminderRequest(
            @Schema(description = "Niveau 1 (J+7), 2 (J+15) ou 3 (J+30)", example = "1")
            @Min(1) @Max(3) int level) {
    }

    @Schema(description = "Ligne avec montants calculés")
    public record LineDto(int position, String label, BigDecimal quantity, BigDecimal unitPrice, BigDecimal vatRate,
                          BigDecimal totalHt, BigDecimal totalVat, BigDecimal totalTtc) {
    }

    @Schema(description = "Paiement")
    public record PaymentDto(UUID id, LocalDate date, BigDecimal amount, PaymentMethod method) {
    }

    @Schema(description = "Avoir")
    public record CreditDto(UUID id, String number, BigDecimal amount, String reason, Instant createdAt) {
    }

    @Schema(description = "Relance")
    public record ReminderDto(int level, Instant sentAt, String sentByName) {
    }

    @Schema(description = "Facture dans une liste ; statut calculé")
    public record InvoiceSummary(UUID id, String number, UUID clientId, String clientName, InvoiceStatus status, LocalDate dueDate,
                                 Instant issuedAt, BigDecimal totalTtc, BigDecimal remaining) {
    }

    @Schema(description = "Détail d'une facture : lignes, totaux, paiements, avoirs, relances")
    public record InvoiceDto(UUID id, String number, UUID clientId, String clientName, InvoiceStatus status, LocalDate dueDate,
                             Instant issuedAt, List<LineDto> lines, BigDecimal totalHt, BigDecimal totalVat, BigDecimal totalTtc,
                             BigDecimal paid, BigDecimal credited, BigDecimal remaining, long daysOverdue,
                             List<PaymentDto> payments, List<CreditDto> creditNotes, List<ReminderDto> reminders, Integer reminderDue) {
    }

    @Schema(description = "Relance à envoyer : plus haut niveau atteint et non encore envoyé")
    public record ReminderDueDto(UUID invoiceId, String number, String clientName, LocalDate dueDate, long daysOverdue,
                                 BigDecimal remaining, int level) {
    }

    @Schema(description = "Tableau de bord de l'année")
    public record StatsDto(int year, BigDecimal invoiced, BigDecimal credited, BigDecimal collected, BigDecimal outstanding,
                           long overdueCount, BigDecimal overdueAmount) {
    }
}
