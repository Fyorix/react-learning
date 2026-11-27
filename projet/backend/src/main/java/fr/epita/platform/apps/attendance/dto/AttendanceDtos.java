package fr.epita.platform.apps.attendance.dto;

import fr.epita.platform.apps.attendance.entity.JustificationStatus;
import fr.epita.platform.apps.attendance.entity.PresenceStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    @Schema(description = "Groupe d'étudiants (ADMIN)")
    public record GroupRequest(
            @Schema(description = "Nom unique, 60 caractères maximum", example = "MTI 2026 — TD 1")
            @NotBlank @Size(max = 60) String name,
            @Schema(description = "Étudiants (chacun dans un seul groupe)")
            @NotNull @Size(max = 500) List<UUID> studentIds) {
    }

    @Schema(description = "Séance (ADMIN) ; `repeatWeeks` crée la même séance plusieurs semaines de suite")
    public record SessionRequest(
            @Schema(description = "Matière, 80 caractères maximum", example = "React")
            @NotBlank @Size(max = 80) String subject,
            @Schema(description = "Groupe", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID groupId,
            @Schema(description = "Enseignant (rôle TEACHER)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID teacherId,
            @Schema(description = "Début (ISO-8601)", example = "2026-12-07T09:00:00+01:00")
            @NotNull Instant start,
            @Schema(description = "Fin (ISO-8601), postérieure au début, 8 h maximum", example = "2026-12-07T12:00:00+01:00")
            @NotNull Instant end,
            @Schema(description = "Nombre de semaines consécutives, de 1 (défaut) à 20", example = "1")
            @Min(1) @Max(20) Integer repeatWeeks) {
    }

    @Schema(description = "Appel d'une séance")
    public record RollCallRequest(
            @Schema(description = "Une ligne par étudiant du groupe ; les étudiants absents de la liste restent non pointés")
            @NotNull @Size(max = 500) List<@Valid RollCallEntry> entries) {
    }

    @Schema(description = "Ligne d'appel")
    public record RollCallEntry(
            @Schema(description = "Étudiant du groupe", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID studentId,
            @Schema(description = "PRESENT, ABSENT ou LATE", example = "PRESENT")
            @NotNull PresenceStatus status) {
    }

    @Schema(description = "Justificatif (partie `data` du multipart ; fichier PDF, JPEG ou PNG dans la partie `file`)")
    public record JustificationRequest(
            @Schema(description = "Absences couvertes (identifiants renvoyés par `/me`)")
            @NotEmpty @Size(max = 50) List<UUID> absenceIds,
            @Schema(description = "Motif, 1 000 caractères maximum", example = "Rendez-vous médical")
            @NotBlank @Size(max = 1000) String reason) {
    }

    @Schema(description = "Refus d'un justificatif (motif obligatoire)")
    public record RejectRequest(
            @Schema(description = "Motif, 500 caractères maximum", example = "Document illisible")
            @NotBlank @Size(max = 500) String reason) {
    }

    @Schema(description = "Groupe")
    public record GroupDto(UUID id, String name, int students) {
    }

    @Schema(description = "Séance")
    public record SessionDto(UUID id, String subject, UUID groupId, String groupName, UUID teacherId, String teacherName, Instant start, Instant end,
                             int recorded, int students, boolean editable, Instant lockedAt) {
    }

    @Schema(description = "Étudiant et son statut à l'appel (`status` nul : non pointé)")
    public record RollCallLine(UUID studentId, String name, PresenceStatus status) {
    }

    @Schema(description = "Séance et appel")
    public record SessionDetailDto(SessionDto session, List<RollCallLine> lines) {
    }

    @Schema(description = "Absence ou retard ; `justification` : statut du dernier justificatif")
    public record AbsenceDto(UUID id, UUID sessionId, String subject, Instant start, Instant end, PresenceStatus status,
                             JustificationStatus justification, Instant justifyBefore, boolean justifiable) {
    }

    @Schema(description = "Compteurs d'une matière : 3 retards valent 1 absence non justifiée")
    public record CounterDto(String subject, int justified, int unjustified, int late, int effectiveUnjustified, boolean alert) {
    }

    @Schema(description = "Mes absences, retards et compteurs")
    public record MyAttendanceDto(List<AbsenceDto> absences, List<CounterDto> counters) {
    }

    @Schema(description = "Justificatif")
    public record JustificationDto(UUID id, UUID studentId, String studentName, String reason, JustificationStatus status, String fileName,
                                   String adminReason, String decidedByName, List<AbsenceDto> absences, Instant createdAt) {
    }

    @Schema(description = "Alerte : étudiant au-delà de 3 absences non justifiées dans une matière")
    public record AlertDto(UUID studentId, String studentName, String groupName, String subject, int effectiveUnjustified, int late) {
    }
}
