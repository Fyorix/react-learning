package fr.epita.platform.apps.fitness.dto;

import fr.epita.platform.apps.fitness.entity.FitnessBookingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class FitnessDtos {

    private FitnessDtos() {
    }

    @Schema(description = "Nouveau cours collectif")
    public record ClassRequest(
            @Schema(description = "Type de cours, 40 caractères maximum", example = "Yoga")
            @NotBlank @Size(max = 40) String type,
            @Schema(description = "Coach (rôle COACH) ; pour un coach, lui-même par défaut", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            UUID coachId,
            @Schema(description = "Début (ISO-8601), dans le futur", example = "2026-12-02T12:15:00Z")
            @NotNull Instant startsAt,
            @Schema(description = "Durée en minutes, de 15 à 180", example = "45")
            @Min(15) @Max(180) int durationMinutes,
            @Schema(description = "Places, de 1 à 50", example = "12")
            @Min(1) @Max(50) int capacity) {
    }

    @Schema(description = "Cours dans le planning ; `myStatus` : ma réservation éventuelle")
    public record ClassSummary(UUID id, String type, UUID coachId, String coachName, Instant startsAt, int durationMinutes,
                               int capacity, long booked, long waitlisted, boolean cancelled, FitnessBookingStatus myStatus) {
    }

    @Schema(description = "Inscrit (visible du coach et des ADMIN)")
    public record AttendeeDto(UUID userId, String name, FitnessBookingStatus status, int position) {
    }

    @Schema(description = "Détail d'un cours ; `myPosition` : rang en liste d'attente")
    public record ClassDto(ClassSummary info, Integer myPosition, boolean canCancelFree, List<AttendeeDto> attendees) {
    }

    @Schema(description = "Résultat d'une réservation : CONFIRMED (1 crédit débité) ou WAITLISTED (sans débit, avec rang)")
    public record BookingResult(FitnessBookingStatus status, Integer position, int creditsLeft) {
    }

    @Schema(description = "Une de mes réservations")
    public record MyBookingDto(UUID classId, String type, String coachName, Instant startsAt, int durationMinutes, FitnessBookingStatus status,
                               Integer position, boolean debited) {
    }

    @Schema(description = "Mes crédits du mois en cours et mes réservations")
    public record MeDto(String month, int creditsPerMonth, int creditsUsed, int creditsLeft, List<MyBookingDto> bookings) {
    }

    @Schema(description = "Remplissage d'un groupe (type ou coach) sur le mois")
    public record FillRow(String label, long classes, long capacity, long confirmed, int fillRate) {
    }

    @Schema(description = "Statistiques mensuelles")
    public record StatsDto(String month, List<FillRow> byType, List<FillRow> byCoach) {
    }
}
