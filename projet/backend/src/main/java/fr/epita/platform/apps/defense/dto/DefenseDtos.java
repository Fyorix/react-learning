package fr.epita.platform.apps.defense.dto;

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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DefenseDtos {

    private DefenseDtos() {
    }

    @Schema(description = "Créneau de soutenance (ADMIN)")
    public record SlotRequest(
            @Schema(description = "Début (ISO-8601)", example = "2026-12-10T09:00:00Z")
            @NotNull Instant start,
            @Schema(description = "Durée en minutes, de 15 à 180", example = "40")
            @Min(15) @Max(180) int durationMinutes,
            @Schema(description = "Salle, 40 caractères maximum", example = "Amphi 4")
            @NotBlank @Size(max = 40) String room,
            @Schema(description = "Jury : 2 ou 3 enseignants (rôle TEACHER)")
            @NotNull List<UUID> juryIds) {
    }

    @Schema(description = "Groupe de projet (ADMIN)")
    public record GroupRequest(
            @Schema(description = "Nom unique, 80 caractères maximum", example = "Groupe 7 — Covoiturage")
            @NotBlank @Size(max = 80) String name,
            @Schema(description = "Étudiants (1 à 6), chacun dans un seul groupe")
            @NotNull @Size(min = 1, max = 6) List<UUID> studentIds,
            @Schema(description = "Encadrant (enseignant), ne siège pas dans le jury du groupe", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID supervisorId) {
    }

    @Schema(description = "Choix d'un créneau par le groupe")
    public record ChooseRequest(
            @Schema(description = "Créneau libre", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID slotId) {
    }

    @Schema(description = "Note d'un membre du jury")
    public record GradeRequest(
            @Schema(description = "Note de 0 à 20 (au quart de point)", example = "15.5")
            @NotNull @DecimalMin("0.00") @DecimalMax("20.00") @Digits(integer = 2, fraction = 2) BigDecimal grade,
            @Schema(description = "Commentaire, 2 000 caractères maximum", example = "Démonstration claire, tests à compléter")
            @NotBlank @Size(max = 2000) String comment) {
    }

    @Schema(description = "Membre du jury")
    public record JurorDto(UUID teacherId, String name, boolean graded) {
    }

    @Schema(description = "Créneau ; `groupId` null s'il est libre")
    public record SlotDto(UUID id, Instant start, Instant end, int durationMinutes, String room, List<JurorDto> jury,
                          UUID groupId, String groupName, boolean free) {
    }

    @Schema(description = "Groupe")
    public record GroupDto(UUID id, String name, List<StudentDto> students, UUID supervisorId, String supervisorName, SlotDto slot, boolean mine) {
    }

    @Schema(description = "Étudiant")
    public record StudentDto(UUID userId, String name) {
    }

    @Schema(description = "Soutenance où je siège ; `myGrade` null tant que je n'ai pas noté")
    public record JuryDutyDto(SlotDto slot, BigDecimal myGrade, String myComment, boolean done, boolean canGrade) {
    }

    @Schema(description = "Résultat : note finale (moyenne) publiée quand tout le jury a noté")
    public record ResultDto(UUID groupId, String groupName, boolean published, BigDecimal finalGrade, int gradesReceived, int jurySize,
                            List<GradeDto> grades) {
    }

    @Schema(description = "Note d'un membre du jury (visible une fois le résultat publié)")
    public record GradeDto(String teacherName, BigDecimal grade, String comment) {
    }

    @Schema(description = "Vue d'ensemble regroupée par salle ou par enseignant")
    public record PlanningRow(String label, List<SlotDto> slots) {
    }
}
