package fr.epita.platform.apps.crm.dto;

import fr.epita.platform.apps.crm.entity.AccountSize;
import fr.epita.platform.apps.crm.entity.InteractionType;
import fr.epita.platform.apps.crm.entity.PipelineStage;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CrmDtos {

    private CrmDtos() {
    }

    @Schema(description = "Compte client ; `ownerId` facultatif (moi par défaut)")
    public record AccountRequest(
            @Schema(description = "Raison sociale, 120 caractères maximum", example = "Acme Industrie")
            @NotBlank @Size(max = 120) String name,
            @Schema(description = "Secteur, 80 caractères maximum", example = "Aéronautique")
            @NotBlank @Size(max = 80) String industry,
            @Schema(description = "Taille : TPE, PME, ETI ou GE", example = "ETI")
            @NotNull AccountSize size,
            @Schema(description = "Commercial responsable (rôle SALES)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            UUID ownerId) {
    }

    @Schema(description = "Contact d'un compte ; e-mail unique dans le compte")
    public record ContactRequest(
            @Schema(description = "Prénom, 60 caractères maximum", example = "Claire")
            @NotBlank @Size(max = 60) String firstName,
            @Schema(description = "Nom, 60 caractères maximum", example = "Durand")
            @NotBlank @Size(max = 60) String lastName,
            @Schema(description = "E-mail", example = "claire.durand@acme.example")
            @NotBlank @Email @Size(max = 254) String email,
            @Schema(description = "Téléphone (facultatif), 30 caractères maximum", example = "+33 6 12 34 56 78")
            @Size(max = 30) String phone,
            @Schema(description = "Fonction, 80 caractères maximum", example = "Directrice des achats")
            @NotBlank @Size(max = 80) String role) {
    }

    @Schema(description = "Opportunité (créée en QUALIFICATION)")
    public record OpportunityRequest(
            @Schema(description = "Compte", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID accountId,
            @Schema(description = "Intitulé, 120 caractères maximum", example = "Renouvellement licences 2027")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Montant en euros", example = "48000")
            @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal amount,
            @Schema(description = "Date de signature prévue", example = "2027-03-31")
            @NotNull LocalDate expectedCloseDate) {
    }

    @Schema(description = "Changement d'étape : étape voisine, ou clôture WON / LOST depuis n'importe quelle étape")
    public record StageRequest(
            @Schema(description = "Nouvelle étape", example = "PROPOSAL")
            @NotNull PipelineStage stage,
            @Schema(description = "Motif, obligatoire pour LOST (500 caractères maximum)", example = "Budget gelé par le client")
            @Size(max = 500) String lostReason) {
    }

    @Schema(description = "Interaction : sur un compte ou une opportunité (le compte est alors déduit)")
    public record InteractionRequest(
            @Schema(description = "Compte", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            UUID accountId,
            @Schema(description = "Opportunité", example = "5d1c7e0a-2b8f-4a61-9e3d-7c4b1a2f3e90")
            UUID opportunityId,
            @Schema(description = "CALL, EMAIL ou MEETING", example = "CALL")
            @NotNull InteractionType type,
            @Schema(description = "Date, pas dans le futur", example = "2026-11-20")
            @NotNull LocalDate date,
            @Schema(description = "Résumé, 2 000 caractères maximum", example = "Point budget : décision en janvier")
            @NotBlank @Size(max = 2000) String summary) {
    }

    @Schema(description = "Tâche de relance (à moi)")
    public record TaskRequest(
            @Schema(description = "Échéance", example = "2026-12-01")
            @NotNull LocalDate dueDate,
            @Schema(description = "Libellé, 200 caractères maximum", example = "Relancer Claire sur la proposition")
            @NotBlank @Size(max = 200) String label,
            @Schema(description = "Compte lié (facultatif)", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            UUID accountId,
            @Schema(description = "Opportunité liée (facultative)", example = "5d1c7e0a-2b8f-4a61-9e3d-7c4b1a2f3e90")
            UUID opportunityId) {
    }

    @Schema(description = "Mise à jour d'une tâche")
    public record TaskPatch(
            @Schema(description = "Faite", example = "true")
            @NotNull Boolean done) {
    }

    @Schema(description = "Compte (liste)")
    public record AccountSummary(UUID id, String name, String industry, AccountSize size, UUID ownerId, String ownerName, int openOpportunities, BigDecimal openAmount) {
    }

    @Schema(description = "Contact")
    public record ContactDto(UUID id, String firstName, String lastName, String email, String phone, String role) {
    }

    @Schema(description = "Opportunité ; `weighted` = montant × probabilité")
    public record OpportunityDto(UUID id, UUID accountId, String accountName, UUID ownerId, String ownerName, String title, BigDecimal amount,
                                 LocalDate expectedCloseDate, PipelineStage stage, int probability, BigDecimal weighted, String lostReason,
                                 Instant closedAt, boolean editable) {
    }

    @Schema(description = "Interaction")
    public record InteractionDto(UUID id, UUID accountId, UUID opportunityId, String opportunityTitle, InteractionType type, LocalDate date, String summary, String authorName) {
    }

    @Schema(description = "Fiche compte : contacts, opportunités, interactions")
    public record AccountDto(UUID id, String name, String industry, AccountSize size, UUID ownerId, String ownerName, boolean editable,
                             List<ContactDto> contacts, List<OpportunityDto> opportunities, List<InteractionDto> interactions) {
    }

    @Schema(description = "Colonne du pipeline")
    public record StageColumn(PipelineStage stage, int probability, List<OpportunityDto> opportunities, BigDecimal total, BigDecimal weighted) {
    }

    @Schema(description = "Détail d'une opportunité et de ses interactions")
    public record OpportunityDetailDto(OpportunityDto opportunity, List<InteractionDto> interactions, List<PipelineStage> allowedStages) {
    }

    @Schema(description = "Tâche de relance")
    public record TaskDto(UUID id, LocalDate dueDate, String label, boolean done, boolean overdue, UUID accountId, String accountName, UUID opportunityId, String opportunityTitle) {
    }

    @Schema(description = "Prévision d'un mois (AAAA-MM)")
    public record ForecastMonth(String month, BigDecimal weighted, BigDecimal amount, int opportunities) {
    }

    @Schema(description = "Prévisions pondérées ; `overdueWeighted` : opportunités ouvertes dont la date prévue est passée")
    public record ForecastDto(List<ForecastMonth> months, BigDecimal total, BigDecimal overdueWeighted) {
    }
}
