package fr.epita.platform.apps.room.dto;

import fr.epita.platform.apps.room.entity.Equipment;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class RoomDtos {

    private RoomDtos() {
    }

    @Schema(description = "Création ou modification d'une salle (ADMIN)")
    public record RoomRequest(
            @Schema(description = "Nom unique de la salle, 80 caractères maximum", example = "Salle Turing")
            @NotBlank @Size(max = 80) String name,
            @Schema(description = "Étage ou zone, 30 caractères maximum", example = "2e étage")
            @NotBlank @Size(max = 30) String floor,
            @Schema(description = "Nombre de places, de 1 à 200", example = "8")
            @NotNull @Min(1) @Max(200) Integer capacity,
            @Schema(description = "Équipements : SCREEN = écran, VISIO = visioconférence, WHITEBOARD = tableau blanc", example = "[\"SCREEN\", \"VISIO\"]")
            @NotNull Set<Equipment> equipment,
            @Schema(description = "false : salle désactivée, plus réservable ; ses réservations futures sont annulées. true par défaut", example = "true")
            Boolean active) {
    }

    @Schema(description = "Salle")
    public record RoomDto(UUID id, String name, String floor, int capacity, Set<Equipment> equipment, boolean active) {
    }

    @Schema(description = "Réservation ou modification d'une réservation")
    public record BookingRequest(
            @Schema(description = "Début (ISO 8601) : aligné sur 30 minutes, entre 8 h et 20 h (heure de Paris), ni passé ni à plus de 30 jours", example = "2026-12-01T09:00:00+01:00")
            @NotNull Instant start,
            @Schema(description = "Fin (ISO 8601), même jour, alignée sur 30 minutes ; durée de 30 min à 4 h", example = "2026-12-01T10:30:00+01:00")
            @NotNull Instant end,
            @Schema(description = "Objet de la réunion, 120 caractères maximum", example = "Point d'équipe")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Nombre de participants, au plus la capacité de la salle", example = "6")
            @NotNull @Min(1) Integer attendees) {
    }

    @Schema(description = "Réservation")
    public record BookingDto(UUID id, UUID roomId, String roomName, UUID userId, String userName, String title,
                             int attendees, Instant start, Instant end, boolean mine, boolean canEdit) {
    }

    @Schema(description = "Taux d'occupation d'une salle sur une période")
    public record OccupancyDto(UUID roomId, String roomName, double bookedHours, double availableHours, int rate) {
    }
}
