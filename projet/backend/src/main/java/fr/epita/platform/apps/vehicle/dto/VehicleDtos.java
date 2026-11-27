package fr.epita.platform.apps.vehicle.dto;

import fr.epita.platform.apps.vehicle.entity.VehicleEnums.BookingStatus;
import fr.epita.platform.apps.vehicle.entity.VehicleEnums.Severity;
import fr.epita.platform.apps.vehicle.entity.VehicleEnums.VehicleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class VehicleDtos {

    private VehicleDtos() {
    }

    @Schema(description = "Ajout ou modification d'un véhicule (ADMIN)")
    public record VehicleRequest(
            @Schema(description = "Immatriculation unique au format AA-123-AA", example = "GA-123-BC")
            @NotBlank @Pattern(regexp = "[A-Z]{2}-[0-9]{3}-[A-Z]{2}") String plate,
            @Schema(description = "Modèle, 80 caractères maximum", example = "Renault Zoé")
            @NotBlank @Size(max = 80) String model,
            @Schema(description = "Nombre de places, de 1 à 9", example = "5")
            @NotNull @Min(1) @Max(9) Integer seats,
            @Schema(description = "Permis requis : B (voiture) ou C (utilitaire lourd)", example = "B", allowableValues = {"B", "C"})
            @NotNull @Pattern(regexp = "B|C") String licenseType,
            @Schema(description = "AVAILABLE = disponible, MAINTENANCE = en entretien (non réservable), RETIRED = retiré du parc. AVAILABLE par défaut", example = "AVAILABLE")
            VehicleStatus status,
            @Schema(description = "Kilométrage actuel, ≥ 0 (à la création)", example = "12000")
            @NotNull @Min(0) @Max(999999) Integer mileage) {
    }

    @Schema(description = "Réservation d'un véhicule")
    public record BookingRequest(
            @Schema(description = "Début (ISO 8601), dans le futur", example = "2026-12-02T08:00:00+01:00")
            @NotNull Instant start,
            @Schema(description = "Fin (ISO 8601), après le début, 5 jours maximum", example = "2026-12-02T18:00:00+01:00")
            @NotNull Instant end,
            @Schema(description = "Motif du déplacement, 200 caractères maximum", example = "Rendez-vous client")
            @NotBlank @Size(max = 200) String purpose,
            @Schema(description = "Destination, 120 caractères maximum", example = "Lyon")
            @NotBlank @Size(max = 120) String destination) {
    }

    @Schema(description = "Relevé de kilométrage au départ")
    public record CheckoutRequest(
            @Schema(description = "Kilométrage au compteur, au moins celui connu du véhicule", example = "12050")
            @NotNull @Min(0) Integer mileage) {
    }

    @Schema(description = "Problème signalé au retour")
    public record IssueRequest(
            @Schema(description = "MINOR = mineur, BLOCKING = bloquant (le véhicule passe en entretien et ses réservations futures sont annulées)", example = "MINOR")
            @NotNull Severity severity,
            @Schema(description = "Description, 1000 caractères maximum", example = "Voyant de pression des pneus allumé")
            @NotBlank @Size(max = 1000) String description) {
    }

    @Schema(description = "Retour du véhicule")
    public record CheckinRequest(
            @Schema(description = "Kilométrage au retour, au moins celui du départ", example = "12480")
            @NotNull @Min(0) Integer mileage,
            @Schema(description = "Problème facultatif")
            @Valid IssueRequest issue) {
    }

    @Schema(description = "Véhicule")
    public record VehicleDto(UUID id, String plate, String model, int seats, String licenseType, VehicleStatus status,
                             int mileage, int kmSinceService, boolean serviceDue) {
    }

    @Schema(description = "Réservation")
    public record BookingDto(UUID id, UUID vehicleId, String vehicleLabel, UUID userId, String userName, Instant start,
                             Instant end, String purpose, String destination, BookingStatus status, Integer startMileage,
                             Integer endMileage, boolean mine) {
    }

    @Schema(description = "Problème signalé")
    public record IssueDto(UUID id, UUID vehicleId, String vehicleLabel, Severity severity, String description, Instant createdAt) {
    }

    @Schema(description = "Suivi de la flotte (ADMIN)")
    public record MaintenanceDto(List<VehicleDto> serviceDue, List<IssueDto> openIssues) {
    }
}
