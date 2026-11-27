package fr.epita.platform.apps.expense.dto;

import fr.epita.platform.apps.expense.entity.ExpenseEnums.Category;
import fr.epita.platform.apps.expense.entity.ExpenseEnums.ClaimStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    @Schema(description = """
            Dépense (partie `data` du formulaire multipart, accompagnée du fichier `receipt`). \
            Plafonds : MEAL 25 €, HOTEL 120 € par nuit ; au-delà, `justification` est obligatoire. \
            Une dépense de plus de 3 mois est refusée.""")
    public record ItemRequest(
            @Schema(description = "Date de la dépense (AAAA-MM-JJ), pas dans le futur ; elle détermine la note mensuelle", example = "2026-11-18")
            @NotNull @PastOrPresent LocalDate date,
            @Schema(description = "Catégorie : MEAL = repas (25 € max), HOTEL = hôtel (120 €/nuit max), TRANSPORT = transport, OTHER = autre", example = "MEAL")
            @NotNull Category category,
            @Schema(description = "Montant TTC en euros, 2 décimales maximum, de 0,01 à 10 000", example = "23.50")
            @NotNull @DecimalMin("0.01") @DecimalMax("10000") @Digits(integer = 6, fraction = 2) BigDecimal amount,
            @Schema(description = "Description, 300 caractères maximum", example = "Déjeuner client Acme")
            @NotBlank @Size(max = 300) String description,
            @Schema(description = "Justification du dépassement de plafond (obligatoire seulement si le plafond est dépassé), 500 caractères maximum", example = "Dîner avec deux clients")
            @Size(max = 500) String justification) {
    }

    @Schema(description = "Décision du manager sur une ligne")
    public record ItemDecision(
            @Schema(description = "Ligne concernée", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID itemId,
            @Schema(description = "true : dépense acceptée, false : refusée", example = "true")
            @NotNull Boolean accepted,
            @Schema(description = "Motif, obligatoire si la ligne est refusée, 500 caractères maximum", example = "Justificatif illisible")
            @Size(max = 500) String reason) {
    }

    @Schema(description = "Revue d'une note soumise : une décision pour chaque ligne")
    public record ReviewRequest(
            @Schema(description = "Décisions, une par ligne de la note")
            @NotEmpty List<@Valid ItemDecision> items) {
    }

    @Schema(description = "Dépense")
    public record ItemDto(UUID id, LocalDate date, Category category, BigDecimal amount, String description,
                          String justification, boolean overCap, String receiptName, String receiptType,
                          Boolean accepted, String rejectReason) {
    }

    @Schema(description = "Note de frais mensuelle")
    public record ClaimDto(UUID id, UUID userId, String userName, String month, ClaimStatus status, List<ItemDto> items,
                           BigDecimal total, BigDecimal reimbursable, Instant submittedAt, String reviewedByName,
                           Instant reviewedAt, Instant reimbursedAt, boolean editable) {
    }

    @Schema(description = "Note dans une liste")
    public record ClaimSummaryDto(UUID id, UUID userId, String userName, String month, ClaimStatus status,
                                  int itemCount, BigDecimal total, BigDecimal reimbursable) {
    }

    @Schema(description = "Montants déclarés (hors lignes refusées) par catégorie et par mois")
    public record StatsDto(int year, Map<Category, BigDecimal> byCategory, Map<String, BigDecimal> byMonth, BigDecimal total) {
    }
}
