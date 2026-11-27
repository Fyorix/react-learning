package fr.epita.platform.apps.grade.dto;

import fr.epita.platform.apps.grade.entity.MarkStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class GradeDtos {

    private GradeDtos() {
    }

    @Schema(description = "Classe (ADMIN)")
    public record ClassRequest(
            @Schema(description = "Nom unique, 60 caractères maximum", example = "MTI 2026 — groupe A")
            @NotBlank @Size(max = 60) String name,
            @Schema(description = "Étudiants (chacun dans une seule classe)")
            @NotNull @Size(max = 200) List<UUID> studentIds) {
    }

    @Schema(description = "Matière (ADMIN)")
    public record SubjectRequest(
            @Schema(description = "Nom, unique dans la classe, 80 caractères maximum", example = "React")
            @NotBlank @Size(max = 80) String name,
            @Schema(description = "Coefficient, de 0.5 à 10", example = "3")
            @NotNull @DecimalMin("0.5") @DecimalMax("10") @Digits(integer = 2, fraction = 2) BigDecimal coefficient,
            @Schema(description = "Enseignant (rôle TEACHER)", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID teacherId,
            @Schema(description = "Classe", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID classId) {
    }

    @Schema(description = "Période (ADMIN)")
    public record PeriodRequest(
            @Schema(description = "Nom unique, 60 caractères maximum", example = "Semestre 1")
            @NotBlank @Size(max = 60) String name,
            @Schema(description = "Premier jour", example = "2026-09-01")
            @NotNull LocalDate start,
            @Schema(description = "Dernier jour, postérieur ou égal au premier", example = "2027-01-31")
            @NotNull LocalDate end) {
    }

    @Schema(description = "Évaluation (TEACHER de la matière)")
    public record AssessmentRequest(
            @Schema(description = "Période ouverte, contenant la date", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID periodId,
            @Schema(description = "Intitulé, 120 caractères maximum", example = "Partiel hooks")
            @NotBlank @Size(max = 120) String title,
            @Schema(description = "Date de l'évaluation", example = "2026-11-20")
            @NotNull LocalDate date,
            @Schema(description = "Poids, de 0.5 à 3 (au dixième)", example = "2")
            @NotNull @DecimalMin("0.5") @DecimalMax("3") @Digits(integer = 1, fraction = 1) BigDecimal weight) {
    }

    @Schema(description = "Saisie des notes d'une évaluation")
    public record MarksRequest(
            @Schema(description = "Une ligne par étudiant ; les autres notes sont conservées")
            @NotNull @Size(max = 200) List<@Valid MarkInput> marks) {
    }

    @Schema(description = "Note d'un étudiant : `value` (statut GRADED, défaut) ou statut ABSENT / ABSENT_EXCUSED sans valeur")
    public record MarkInput(
            @Schema(description = "Étudiant de la classe", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID studentId,
            @Schema(description = "Note de 0 à 20 au quart de point", example = "14.75")
            @DecimalMin("0") @DecimalMax("20") @Digits(integer = 2, fraction = 2) BigDecimal value,
            @Schema(description = "GRADED (défaut si `value`), ABSENT (compte 0) ou ABSENT_EXCUSED (exclue)", example = "GRADED")
            MarkStatus status) {
    }

    @Schema(description = "Appréciations d'une matière sur une période")
    public record CommentsRequest(
            @Schema(description = "Une appréciation par étudiant (texte vide : supprimée)")
            @NotNull @Size(max = 200) List<@Valid CommentInput> comments) {
    }

    @Schema(description = "Appréciation")
    public record CommentInput(
            @Schema(description = "Étudiant de la classe", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID studentId,
            @Schema(description = "Texte, 1 000 caractères maximum", example = "Travail sérieux, bonne participation.")
            @NotNull @Size(max = 1000) String text) {
    }

    @Schema(description = "Appréciation générale (ADMIN)")
    public record GeneralCommentRequest(
            @Schema(description = "Texte, 1 000 caractères maximum (vide : supprimée)", example = "Très bon semestre.")
            @NotNull @Size(max = 1000) String text) {
    }

    @Schema(description = "Classe")
    public record ClassDto(UUID id, String name, List<StudentDto> students) {
    }

    @Schema(description = "Étudiant")
    public record StudentDto(UUID userId, String name) {
    }

    @Schema(description = "Matière")
    public record SubjectDto(UUID id, String name, BigDecimal coefficient, UUID teacherId, String teacherName, UUID classId, String className, int students) {
    }

    @Schema(description = "Période")
    public record PeriodDto(UUID id, String name, LocalDate start, LocalDate end, boolean locked) {
    }

    @Schema(description = "Note d'un étudiant à une évaluation")
    public record MarkDto(UUID studentId, String studentName, BigDecimal value, MarkStatus status) {
    }

    @Schema(description = "Évaluation et notes")
    public record AssessmentDto(UUID id, UUID subjectId, String subjectName, UUID periodId, String periodName, boolean periodLocked, String title,
                                LocalDate date, BigDecimal weight, List<MarkDto> marks, BigDecimal average) {
    }

    @Schema(description = "Note d'un étudiant (vue étudiant)")
    public record MyMarkDto(UUID assessmentId, String title, LocalDate date, BigDecimal weight, BigDecimal value, MarkStatus status) {
    }

    @Schema(description = "Moyenne d'une matière ; `average` nul si aucune note")
    public record SubjectAverage(UUID subjectId, String subjectName, BigDecimal coefficient, String teacherName, BigDecimal average,
                                 List<MyMarkDto> marks, String comment) {
    }

    @Schema(description = "Mes notes et moyennes sur une période")
    public record MyGradesDto(UUID periodId, String periodName, boolean locked, String className, List<SubjectAverage> subjects, BigDecimal general) {
    }

    @Schema(description = "Bulletin : moyennes, rang, appréciations")
    public record ReportCardDto(UUID studentId, String studentName, String className, UUID periodId, String periodName, boolean locked,
                                List<SubjectAverage> subjects, BigDecimal general, Integer rank, int classSize, String generalComment) {
    }

    @Schema(description = "Ligne de synthèse : un étudiant")
    public record SummaryRow(UUID studentId, String studentName, List<BigDecimal> subjectAverages, BigDecimal general, Integer rank) {
    }

    @Schema(description = "Synthèse de la classe : colonnes = matières")
    public record ClassSummaryDto(UUID classId, String className, UUID periodId, String periodName, List<SubjectDto> subjects,
                                  List<SummaryRow> rows, List<BigDecimal> subjectClassAverages, BigDecimal classAverage) {
    }
}
