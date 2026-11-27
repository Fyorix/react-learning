package fr.epita.platform.apps.training.dto;

import fr.epita.platform.apps.training.entity.EnrollmentStatus;
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

public final class TrainingDtos {

    private TrainingDtos() {
    }

    @Schema(description = "Création d'une formation (ADMIN)")
    public record CourseRequest(
            @Schema(description = "Titre, 150 caractères maximum", example = "Les bases de React")
            @NotBlank @Size(max = 150) String title,
            @Schema(description = "Thème, 60 caractères maximum", example = "Développement")
            @NotBlank @Size(max = 60) String theme,
            @Schema(description = "Formateur (utilisateur de la plateforme), qui fera l'émargement", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID trainerId,
            @Schema(description = "Durée en heures, de 0,5 à 100", example = "7")
            @NotNull @DecimalMin("0.5") @DecimalMax("100") BigDecimal durationHours,
            @Schema(description = "Description, 2000 caractères maximum", example = "Composants, props, état et hooks.")
            @NotBlank @Size(max = 2000) String description) {
    }

    @Schema(description = "Ajout d'une session datée (ADMIN)")
    public record SessionRequest(
            @Schema(description = "Début (ISO 8601), dans le futur", example = "2026-12-10T09:00:00+01:00")
            @NotNull Instant start,
            @Schema(description = "Fin (ISO 8601), après le début", example = "2026-12-10T17:00:00+01:00")
            @NotNull Instant end,
            @Schema(description = "Lieu, 120 caractères maximum", example = "Salle Lovelace")
            @NotBlank @Size(max = 120) String location,
            @Schema(description = "Nombre de places, de 1 à 200", example = "12")
            @NotNull @Min(1) @Max(200) Integer seats) {
    }

    @Schema(description = "Refus d'une inscription par le manager")
    public record RejectRequest(
            @Schema(description = "Motif, obligatoire, 500 caractères maximum", example = "Période de livraison, à reporter")
            @NotBlank @Size(max = 500) String reason) {
    }

    @Schema(description = "Émargement")
    public record AttendanceRequest(
            @Schema(description = "Participants présents (parmi les inscrits confirmés) ; les autres sont notés absents", example = "[\"9b76d11e-ec02-474c-8ca9-8886c4bfbb7d\"]")
            @NotNull @Size(max = 200) List<UUID> presentUserIds) {
    }

    @Schema(description = "Session")
    public record SessionDto(UUID id, UUID courseId, Instant start, Instant end, String location, int seats,
                             long confirmed, long waitlisted, long remaining, EnrollmentStatus myStatus,
                             Integer myWaitlistPosition, UUID myEnrollmentId, boolean past) {
    }

    @Schema(description = "Formation avec ses sessions à venir")
    public record CourseDto(UUID id, String title, String theme, UUID trainerId, String trainerName, BigDecimal durationHours,
                            String description, List<SessionDto> sessions) {
    }

    @Schema(description = "Inscrit (vue ADMIN et formateur)")
    public record AttendeeDto(UUID enrollmentId, UUID userId, String userName, EnrollmentStatus status, Integer waitlistPosition, Boolean present) {
    }

    @Schema(description = "Détail d'une session")
    public record SessionDetailDto(SessionDto session, String courseTitle, String trainerName, List<AttendeeDto> attendees) {
    }

    @Schema(description = "Inscription")
    public record EnrollmentDto(UUID id, UUID sessionId, UUID userId, String userName, String courseTitle, Instant start,
                                Instant end, String location, EnrollmentStatus status, Integer waitlistPosition,
                                String reason, Boolean present, boolean canCancel) {
    }

    @Schema(description = "Attestation de formation")
    public record CertificateDto(String userName, String courseTitle, String trainerName, LocalDate date, BigDecimal hours, String reference) {
    }

    @Schema(description = "Remplissage d'une session")
    public record FillDto(UUID sessionId, String courseTitle, Instant start, int seats, long confirmed, int rate) {
    }

    @Schema(description = "Statistiques annuelles (ADMIN)")
    public record StatsDto(int year, List<FillDto> sessions, List<HoursDto> hoursByEmployee) {
    }

    @Schema(description = "Heures de formation suivies par un salarié")
    public record HoursDto(UUID userId, String userName, BigDecimal hours) {
    }
}
