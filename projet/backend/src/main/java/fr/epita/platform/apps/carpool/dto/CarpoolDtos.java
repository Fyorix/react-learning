package fr.epita.platform.apps.carpool.dto;

import fr.epita.platform.apps.carpool.entity.TripStatus;
import fr.epita.platform.user.dto.UserDtos.UserSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class CarpoolDtos {

    private CarpoolDtos() {
    }

    @Schema(description = "Publication ou modification d'un trajet")
    public record TripRequest(
            @Schema(description = "Lieu de départ, 100 caractères maximum", example = "Paris - Gare de Lyon")
            @NotBlank @Size(max = 100) String from,
            @Schema(description = "Lieu d'arrivée, 100 caractères maximum", example = "EPITA Villejuif")
            @NotBlank @Size(max = 100) String to,
            @Schema(description = "Date et heure de départ (ISO 8601, UTC), dans le futur", example = "2026-12-01T07:30:00Z")
            @NotNull Instant departureAt,
            @Schema(description = "Nombre de places proposées, de 1 à 8 ; jamais sous le nombre de réservations actives", example = "3")
            @NotNull @Min(1) @Max(8) Integer seats,
            @Schema(description = "Commentaire facultatif, 500 caractères maximum", example = "Départ devant la gare, côté taxis")
            @Size(max = 500) String comment) {
    }

    public record TripDto(UUID id, UserSummary driver, String from, String to, Instant departureAt, int seats,
                          int remainingSeats, String comment, TripStatus status, boolean past, boolean booked,
                          boolean isDriver, List<UserSummary> passengers) {
    }
}
