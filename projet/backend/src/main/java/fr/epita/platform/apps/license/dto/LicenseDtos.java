package fr.epita.platform.apps.license.dto;

import fr.epita.platform.apps.license.entity.Billing;
import fr.epita.platform.apps.license.entity.LicenseRequestStatus;
import fr.epita.platform.apps.license.entity.SoftwareStatus;
import io.swagger.v3.oas.annotations.media.Schema;
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

public final class LicenseDtos {

    private LicenseDtos() {
    }

    @Schema(description = "Logiciel sous abonnement (ADMIN)")
    public record SoftwareRequest(
            @Schema(description = "Nom unique, 100 caractères maximum", example = "Figma")
            @NotBlank @Size(max = 100) String name,
            @Schema(description = "Éditeur, 100 caractères maximum", example = "Figma Inc.")
            @NotBlank @Size(max = 100) String vendor,
            @Schema(description = "Sièges achetés (jamais sous le nombre attribué)", example = "10")
            @Min(0) @Max(10000) int seats,
            @Schema(description = "Coût par siège et par période de facturation, en euros", example = "45.00")
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal costPerSeat,
            @Schema(description = "MONTHLY ou YEARLY", example = "MONTHLY")
            @NotNull Billing billing,
            @Schema(description = "Prochaine date de renouvellement", example = "2027-01-15")
            @NotNull LocalDate renewalDate,
            @Schema(description = "ACTIVE ou TERMINATED (ACTIVE par défaut)", example = "ACTIVE")
            SoftwareStatus status) {
    }

    @Schema(description = "Attribution d'un siège")
    public record AssignRequest(
            @Schema(description = "Salarié", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID userId) {
    }

    @Schema(description = "Demande de licence")
    public record AskLicenseRequest(
            @Schema(description = "Logiciel actif", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID softwareId,
            @Schema(description = "Justification, 500 caractères maximum", example = "Maquettes du nouveau site")
            @NotBlank @Size(max = 500) String reason) {
    }

    @Schema(description = "Refus d'une demande")
    public record RejectRequest(
            @Schema(description = "Motif, 300 caractères maximum", example = "Une licence partagée est disponible")
            @NotBlank @Size(max = 300) String reason) {
    }

    @Schema(description = "Logiciel ; `mine` : l'utilisateur courant détient un siège")
    public record SoftwareDto(UUID id, String name, String vendor, int seats, long assigned, int seatsLeft, BigDecimal costPerSeat,
                              Billing billing, BigDecimal monthlyCostPerSeat, LocalDate renewalDate, SoftwareStatus status,
                              boolean mine, boolean requestPending) {
    }

    @Schema(description = "Détenteur d'un siège ; `unused` : pas d'utilisation déclarée depuis 90 jours")
    public record SeatDto(UUID userId, String name, Instant assignedAt, LocalDate lastUsedOn, boolean unused) {
    }

    @Schema(description = "Demande")
    public record LicenseRequestDto(UUID id, UUID softwareId, String softwareName, UUID userId, String userName, String reason,
                                    LicenseRequestStatus status, String rejectReason, Instant createdAt) {
    }

    @Schema(description = "Une de mes licences")
    public record MyLicenseDto(UUID softwareId, String name, String vendor, Instant assignedAt, LocalDate lastUsedOn) {
    }

    @Schema(description = "Alertes : renouvellements dans moins de 30 jours, sièges inutilisés depuis 90 jours")
    public record AlertsDto(List<SoftwareDto> renewals, List<UnusedSeat> unusedSeats) {
    }

    @Schema(description = "Siège inutilisé")
    public record UnusedSeat(UUID softwareId, String softwareName, UUID userId, String userName, LocalDate lastUsedOn, BigDecimal monthlyCost) {
    }

    @Schema(description = "Ligne de coût mensuel")
    public record CostRow(String label, long seats, BigDecimal monthlyCost) {
    }

    @Schema(description = "Coûts mensuels : par logiciel, par équipe (manager du détenteur) et total")
    public record CostsDto(String month, BigDecimal total, List<CostRow> bySoftware, List<CostRow> byTeam) {
    }
}
