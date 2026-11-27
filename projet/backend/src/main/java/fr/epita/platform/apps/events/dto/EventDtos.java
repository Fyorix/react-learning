package fr.epita.platform.apps.events.dto;

import fr.epita.platform.apps.events.entity.EventStatus;
import fr.epita.platform.apps.events.entity.RegistrationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

public final class EventDtos {

    private EventDtos() {
    }

    @Schema(description = "Création ou modification d'un événement")
    public record EventRequest(
            @Schema(description = "Titre, 150 caractères maximum", example = "Afterwork de Noël")
            @NotBlank @Size(max = 150) String title,
            @Schema(description = "Description, 2000 caractères maximum", example = "Apéro et quiz musical")
            @NotBlank @Size(max = 2000) String description,
            @Schema(description = "Lieu, 200 caractères maximum", example = "Le Comptoir, 12 rue de la Paix")
            @NotBlank @Size(max = 200) String location,
            @Schema(description = "Début (ISO 8601, UTC), dans le futur", example = "2026-12-18T18:30:00Z")
            @NotNull Instant startsAt,
            @Schema(description = "Nombre de places, de 1 à 500 ; jamais sous le nombre de CONFIRMED", example = "40")
            @NotNull @Min(1) @Max(500) Integer capacity,
            @Schema(description = "Date limite d'inscription et de désinscription (ISO 8601, UTC), au plus tard au début", example = "2026-12-16T23:59:00Z")
            @NotNull Instant registrationDeadline) {
    }

    public record RegistrationResultDto(RegistrationStatus status, Integer position) {
    }

    public record AttendeeDto(UUID userId, String displayName, RegistrationStatus status, Integer position,
                              Instant registeredAt) {
    }

    public record EventDto(UUID id, String title, String description, String location, Instant startsAt,
                           int capacity, Instant registrationDeadline, EventStatus status, UUID organizerId,
                           String organizerName, long confirmedCount, long waitlistCount, long remainingSeats,
                           boolean registrationOpen, boolean past, boolean isOrganizer,
                           RegistrationStatus myStatus, Integer myPosition, List<AttendeeDto> attendees) {
    }
}
