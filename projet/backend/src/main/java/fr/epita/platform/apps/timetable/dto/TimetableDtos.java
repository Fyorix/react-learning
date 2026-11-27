package fr.epita.platform.apps.timetable.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class TimetableDtos {

    private TimetableDtos() {
    }

    @Schema(description = "Salle (ADMIN)")
    public record RoomRequest(
            @Schema(description = "Nom unique, 40 caractères maximum", example = "Amphi 4")
            @NotBlank @Size(max = 40) String name,
            @Schema(description = "Capacité, de 1 à 1 000 places", example = "120")
            @Min(1) @Max(1000) int capacity) {
    }

    @Schema(description = "Groupe d'étudiants (ADMIN)")
    public record GroupRequest(
            @Schema(description = "Nom unique, 60 caractères maximum", example = "MTI 2026 — TD 1")
            @NotBlank @Size(max = 60) String name,
            @Schema(description = "Effectif, supérieur ou égal au nombre d'étudiants inscrits", example = "30")
            @Min(1) @Max(1000) int size,
            @Schema(description = "Étudiants (chacun dans un seul groupe)")
            @NotNull @Size(max = 1000) List<UUID> studentIds) {
    }

    @Schema(description = "Matière d'un groupe (ADMIN)")
    public record SubjectRequest(
            @Schema(description = "Nom, unique dans le groupe, 80 caractères maximum", example = "React")
            @NotBlank @Size(max = 80) String name,
            @Schema(description = "Volume prévu en heures, de 1 à 500", example = "24")
            @Min(1) @Max(500) int plannedHours,
            @Schema(description = "Groupe", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID groupId) {
    }

    @Schema(description = "Cours : de 8 h à 20 h (heure de Paris), en quarts d'heure, 30 min à 4 h, le même jour")
    public record CourseRequest(
            @Schema(description = "Matière (son groupe est celui du cours)", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID subjectId,
            @Schema(description = "Enseignant (rôle TEACHER)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID teacherId,
            @Schema(description = "Salle", example = "5d1c7e0a-2b8f-4a61-9e3d-7c4b1a2f3e90")
            @NotNull UUID roomId,
            @Schema(description = "Début (ISO-8601)", example = "2026-12-07T09:00:00+01:00")
            @NotNull Instant start,
            @Schema(description = "Fin (ISO-8601)", example = "2026-12-07T12:00:00+01:00")
            @NotNull Instant end) {
    }

    @Schema(description = "Salle")
    public record RoomDto(UUID id, String name, int capacity) {
    }

    @Schema(description = "Groupe")
    public record GroupDto(UUID id, String name, int size, int enrolled) {
    }

    @Schema(description = "Matière")
    public record SubjectDto(UUID id, String name, int plannedHours, UUID groupId, String groupName) {
    }

    @Schema(description = "Cours placé")
    public record CourseDto(UUID id, UUID subjectId, String subjectName, UUID groupId, String groupName, UUID teacherId, String teacherName,
                            UUID roomId, String roomName, Instant start, Instant end, boolean past) {
    }

    @Schema(description = "Conflit détecté : code d'erreur et message")
    public record ConflictDto(String code, String message) {
    }

    @Schema(description = "Résultat de la vérification d'un cours candidat")
    public record CheckDto(boolean ok, List<ConflictDto> conflicts) {
    }

    @Schema(description = "Volumes horaires d'une matière, en heures")
    public record HoursDto(UUID subjectId, String subjectName, int plannedHours, double scheduledHours, double doneHours) {
    }
}
