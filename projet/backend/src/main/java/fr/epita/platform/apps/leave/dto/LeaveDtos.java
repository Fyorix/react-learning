package fr.epita.platform.apps.leave.dto;

import fr.epita.platform.apps.leave.entity.LeaveEnums.Half;
import fr.epita.platform.apps.leave.entity.LeaveEnums.LeaveStatus;
import fr.epita.platform.apps.leave.entity.LeaveEnums.LeaveType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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

public final class LeaveDtos {

    private LeaveDtos() {
    }

    private static final String TYPE = "PAID = congés payés, RTT = réduction du temps de travail, UNPAID = sans solde (non décompté du solde)";

    @Schema(description = "Demande de congé. Durée calculée en jours ouvrés (week-ends et jours fériés exclus), demi-journées comprises ; les deux dates sont dans la même année.")
    public record LeaveRequestBody(
            @Schema(description = "Type : " + TYPE, example = "PAID")
            @NotNull LeaveType type,
            @Schema(description = "Premier jour (AAAA-MM-JJ)", example = "2026-12-21")
            @NotNull LocalDate startDate,
            @Schema(description = "Dernier jour (AAAA-MM-JJ), ≥ `startDate`, même année", example = "2026-12-24")
            @NotNull LocalDate endDate,
            @Schema(description = "Début le matin (MORNING) ou l'après-midi (AFTERNOON) du premier jour ; MORNING par défaut", example = "MORNING")
            Half startHalf,
            @Schema(description = "Fin le matin (MORNING) ou le soir (AFTERNOON) du dernier jour ; AFTERNOON par défaut", example = "AFTERNOON")
            Half endHalf,
            @Schema(description = "Commentaire facultatif, 500 caractères maximum", example = "Vacances de Noël")
            @Size(max = 500) String comment) {
    }

    @Schema(description = "Refus d'une demande")
    public record RejectRequest(
            @Schema(description = "Motif du refus, obligatoire, 500 caractères maximum", example = "Période de clôture : présence nécessaire")
            @NotBlank @Size(max = 500) String reason) {
    }

    @Schema(description = "Soldes annuels attribués à un salarié (ADMIN)")
    public record BalanceRequest(
            @Schema(description = "Année concernée", example = "2026")
            @NotNull @Min(2000) @Max(2100) Integer year,
            @Schema(description = "Jours de congés payés acquis, de 0 à 60, par demi-journée", example = "25")
            @NotNull @DecimalMin("0") @DecimalMax("60") BigDecimal paid,
            @Schema(description = "Jours de RTT acquis, de 0 à 30, par demi-journée", example = "10")
            @NotNull @DecimalMin("0") @DecimalMax("30") BigDecimal rtt) {
    }

    @Schema(description = "Demande de congé")
    public record LeaveDto(UUID id, UUID userId, String userName, LeaveType type, LocalDate startDate, LocalDate endDate,
                           Half startHalf, Half endHalf, BigDecimal days, LeaveStatus status, String comment,
                           String reason, String decidedByName, Instant decidedAt, boolean canCancel,
                           Instant createdAt) {
    }

    @Schema(description = "Solde d'un type de congé")
    public record BalanceDto(LeaveType type, BigDecimal acquired, BigDecimal taken, BigDecimal pending, BigDecimal remaining) {
    }

    @Schema(description = "Soldes d'un salarié pour une année")
    public record BalancesDto(UUID userId, String userName, int year, List<BalanceDto> balances) {
    }

    @Schema(description = "Absence validée dans le calendrier d'équipe ; `type` n'est visible que du manager")
    public record AbsenceDto(UUID requestId, UUID userId, String userName, LeaveType type, LocalDate startDate,
                             LocalDate endDate, Half startHalf, Half endHalf) {
    }

    @Schema(description = "Aperçu d'une demande avant envoi")
    public record PreviewDto(BigDecimal days, BigDecimal remainingBefore, BigDecimal remainingAfter, boolean enough) {
    }

    @Schema(description = "Ma situation : manager et salariés")
    public record ProfileDto(UUID managerId, String managerName, List<UUID> employeeIds) {
    }
}
