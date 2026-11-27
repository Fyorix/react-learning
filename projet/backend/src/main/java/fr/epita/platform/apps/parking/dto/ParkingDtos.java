package fr.epita.platform.apps.parking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ParkingDtos {

    private ParkingDtos() {
    }

    @Schema(description = "Libération de ma place pour des jours donnés")
    public record ReleaseRequest(
            @Schema(description = "Jours ouvrés à venir (AAAA-MM-JJ), 30 maximum", example = "[\"2026-12-03\", \"2026-12-04\"]")
            @NotEmpty @Size(max = 30) List<LocalDate> dates) {
    }

    @Schema(description = "Réservation d'une place pour un jour")
    public record BookRequest(
            @Schema(description = "Place souhaitée ; si absente, la première place libre est attribuée", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            UUID spotId) {
    }

    @Schema(description = "Création d'une place (ADMIN)")
    public record SpotRequest(
            @Schema(description = "Numéro unique, 1 à 10 caractères (lettres, chiffres, tirets)", example = "B-12")
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{1,10}") String number,
            @Schema(description = "Niveau, 20 caractères maximum", example = "Sous-sol -1")
            @NotBlank @Size(max = 20) String level) {
    }

    @Schema(description = "Attribution d'une place à un titulaire (ADMIN)")
    public record HolderRequest(
            @Schema(description = "Titulaire, ou null pour libérer définitivement la place ; un titulaire a au plus une place", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            UUID holderId) {
    }

    @Schema(description = """
            Situation d'une place pour un jour : HOLDER = occupée par son titulaire, \
            RELEASED = libérée et libre, FREE = sans titulaire et libre, BOOKED = réservée""")
    public record SpotDayDto(UUID spotId, String number, String level, String state, UUID holderId, String holderName,
                             UUID occupantId, String occupantName, boolean mine) {
    }

    @Schema(description = "Situation du parking pour un jour")
    public record DayDto(LocalDate day, boolean workingDay, long freeSpots, List<SpotDayDto> spots) {
    }

    @Schema(description = "Ma situation : ma place, mes libérations et réservations à venir")
    public record MeDto(SpotDto spot, List<LocalDate> releases, List<LocalDate> releasesTaken, List<MyBookingDto> bookings) {
    }

    @Schema(description = "Place")
    public record SpotDto(UUID id, String number, String level, UUID holderId, String holderName) {
    }

    @Schema(description = "Ma réservation")
    public record MyBookingDto(UUID id, LocalDate day, String spotNumber, String level) {
    }

    @Schema(description = "Utilisation d'une place sur un mois (jours ouvrés occupés)")
    public record UsageDto(UUID spotId, String number, long usedDays, long workingDays, int rate) {
    }
}
