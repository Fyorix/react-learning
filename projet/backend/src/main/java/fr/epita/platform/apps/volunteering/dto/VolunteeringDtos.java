package fr.epita.platform.apps.volunteering.dto;

import fr.epita.platform.apps.volunteering.entity.VolunteerStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class VolunteeringDtos {

    private VolunteeringDtos() {
    }

    @Schema(description = "Association partenaire (ADMIN)")
    public record AssociationRequest(
            @Schema(description = "Nom unique, 120 caractères maximum", example = "Les Restos du Cœur")
            @NotBlank @Size(max = 120) String name,
            @Schema(description = "Cause, 60 caractères maximum", example = "Solidarité")
            @NotBlank @Size(max = 60) String cause,
            @Schema(description = "Active : propose des missions", example = "true")
            @NotNull Boolean active) {
    }

    @Schema(description = "Mission (ADMIN)")
    public record MissionRequest(
            @Schema(description = "Association active", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID associationId,
            @Schema(description = "Intitulé, 150 caractères maximum", example = "Collecte alimentaire")
            @NotBlank @Size(max = 150) String title,
            @Schema(description = "Date, dans le futur", example = "2026-12-12")
            @NotNull LocalDate date,
            @Schema(description = "Durée en heures, de 1 à 14", example = "4")
            @Min(1) @Max(14) int durationHours,
            @Schema(description = "Lieu, 150 caractères maximum", example = "Carrefour Market, Paris 11e")
            @NotBlank @Size(max = 150) String location,
            @Schema(description = "Compétences utiles (10 maximum)", example = "[\"logistique\"]")
            @Size(max = 10) List<@Size(max = 30) String> skills,
            @Schema(description = "Places, de 1 à 100", example = "6")
            @Min(1) @Max(100) int seats) {
    }

    @Schema(description = "Refus d'une inscription")
    public record RejectRequest(
            @Schema(description = "Motif, 300 caractères maximum", example = "Période de clôture comptable")
            @NotBlank @Size(max = 300) String reason) {
    }

    @Schema(description = "Déclaration des heures effectuées")
    public record HoursRequest(
            @Schema(description = "Heures (au demi-heure près), plafonnées à la durée de la mission", example = "3.5")
            @NotNull @DecimalMin("0.5") @DecimalMax("14.0") @Digits(integer = 2, fraction = 1) BigDecimal hours,
            @Schema(description = "Commentaire, 1 000 caractères maximum", example = "Super ambiance, à refaire !")
            @Size(max = 1000) String comment) {
    }

    @Schema(description = "Association")
    public record AssociationDto(UUID id, String name, String cause, boolean active) {
    }

    @Schema(description = "Mission ; `seatsLeft` tient compte des inscriptions en attente et approuvées")
    public record MissionDto(UUID id, UUID associationId, String associationName, String cause, String title, LocalDate date,
                             int durationHours, String location, List<String> skills, int seats, int seatsLeft, VolunteerStatus myStatus) {
    }

    @Schema(description = "Inscription")
    public record RegistrationDto(UUID id, UUID missionId, String missionTitle, String associationName, LocalDate date, int durationHours,
                                  UUID userId, String userName, VolunteerStatus status, String reason, BigDecimal hours,
                                  String comment, boolean canDeclareHours) {
    }

    @Schema(description = "Mes missions et mon compteur annuel (plafond 14 h)")
    public record MeDto(int year, BigDecimal plannedHours, BigDecimal declaredHours, int limitHours, BigDecimal remainingHours,
                        List<RegistrationDto> registrations) {
    }

    @Schema(description = "Ligne du bilan")
    public record ReportRow(String label, BigDecimal hours, long missions) {
    }

    @Schema(description = "Bilan annuel : heures déclarées par salarié, par association et au total")
    public record ReportDto(int year, BigDecimal totalHours, long volunteers, List<ReportRow> byEmployee, List<ReportRow> byAssociation) {
    }
}
