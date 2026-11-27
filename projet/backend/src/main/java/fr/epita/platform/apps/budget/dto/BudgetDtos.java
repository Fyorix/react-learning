package fr.epita.platform.apps.budget.dto;

import fr.epita.platform.apps.budget.entity.BudgetCategory;
import fr.epita.platform.apps.budget.entity.PurchaseStatus;
import fr.epita.platform.apps.budget.entity.YearStatus;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BudgetDtos {

    private BudgetDtos() {
    }

    @Schema(description = "Équipe (ADMIN)")
    public record TeamRequest(
            @Schema(description = "Nom unique, 80 caractères maximum", example = "Équipe Data")
            @NotBlank @Size(max = 80) String name,
            @Schema(description = "Manager : valide les demandes", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID managerId,
            @Schema(description = "Membres pouvant faire des demandes")
            @NotNull @Size(max = 200) List<UUID> memberIds) {
    }

    @Schema(description = "Enveloppe d'une catégorie")
    public record EnvelopeItem(
            @Schema(description = "TRAINING, EQUIPMENT, EVENTS ou OTHER", example = "TRAINING")
            @NotNull BudgetCategory category,
            @Schema(description = "Montant annuel en euros", example = "12000.00")
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount) {
    }

    @Schema(description = "Enveloppes d'une équipe pour un exercice (ADMIN)")
    public record EnvelopesRequest(
            @Schema(description = "Exercice", example = "2026")
            @Min(2000) @Max(2100) int year,
            @Schema(description = "Enveloppes (les catégories absentes ne changent pas)")
            @NotNull @Size(max = 4) List<@Valid EnvelopeItem> envelopes) {
    }

    @Schema(description = "Demande d'achat")
    public record PurchaseRequestBody(
            @Schema(description = "Équipe (dont je suis membre)", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID teamId,
            @Schema(description = "Catégorie", example = "EQUIPMENT")
            @NotNull BudgetCategory category,
            @Schema(description = "Objet, 200 caractères maximum", example = "Écran 27 pouces")
            @NotBlank @Size(max = 200) String label,
            @Schema(description = "Montant estimé en euros", example = "320.00")
            @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal estimatedAmount,
            @Schema(description = "Fournisseur (facultatif)", example = "LDLC")
            @Size(max = 120) String supplier) {
    }

    @Schema(description = "Validation ; dérogation au restant réservée aux ADMIN (motif obligatoire)")
    public record ApproveRequest(
            @Schema(description = "Dépasser le restant de la catégorie (ADMIN)", example = "false")
            boolean override,
            @Schema(description = "Motif de la dérogation, 500 caractères maximum", example = "Urgence : remplacement d'un PC en panne")
            @Size(max = 500) String reason) {
    }

    @Schema(description = "Refus")
    public record RejectRequest(
            @Schema(description = "Motif, 500 caractères maximum", example = "À reporter au prochain trimestre")
            @NotBlank @Size(max = 500) String reason) {
    }

    @Schema(description = "Dépense réelle : remplace l'engagement")
    public record SpendRequest(
            @Schema(description = "Montant réel en euros", example = "299.90")
            @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Schema(description = "Date de la dépense, pas dans le futur", example = "2026-11-20")
            @NotNull LocalDate date) {
    }

    @Schema(description = "Équipe")
    public record TeamDto(UUID id, String name, UUID managerId, String managerName, List<UUID> memberIds, boolean manager, boolean member) {
    }

    @Schema(description = "Catégorie du tableau de bord ; `consumption` = (engagé + dépensé) / prévu en %, `alert` = 80 ou 100")
    public record CategoryDto(BudgetCategory category, BigDecimal planned, BigDecimal committed, BigDecimal spent, BigDecimal remaining,
                              int consumption, Integer alert) {
    }

    @Schema(description = "Tableau de bord d'une équipe pour un exercice")
    public record DashboardDto(UUID teamId, String teamName, int year, YearStatus yearStatus, List<CategoryDto> categories,
                               BigDecimal planned, BigDecimal committed, BigDecimal spent, BigDecimal remaining) {
    }

    @Schema(description = "Demande d'achat")
    public record PurchaseDto(UUID id, UUID teamId, String teamName, int year, UUID requesterId, String requesterName, BudgetCategory category,
                              String label, BigDecimal estimatedAmount, String supplier, PurchaseStatus status, BigDecimal spentAmount,
                              LocalDate spentOn, String decidedByName, String reason, String overrideReason, Instant createdAt) {
    }

    @Schema(description = "Alerte de consommation (80 ou 100 %)")
    public record AlertDto(UUID teamId, String teamName, int year, BudgetCategory category, int consumption, int level) {
    }
}
