package fr.epita.platform.apps.timesheet.dto;

import fr.epita.platform.apps.timesheet.entity.WeekStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TimesheetDtos {

    private TimesheetDtos() {
    }

    @Schema(description = "Création ou modification d'un projet (ADMIN)")
    public record ProjectRequest(
            @Schema(description = "Code unique, 2 à 20 caractères (majuscules, chiffres, tirets)", example = "ACME-WEB")
            @NotBlank @Pattern(regexp = "[A-Z0-9-]{2,20}") String code,
            @Schema(description = "Nom du projet, 120 caractères maximum", example = "Refonte du site Acme")
            @NotBlank @Size(max = 120) String name,
            @Schema(description = "Client facultatif, 120 caractères maximum", example = "Acme")
            @Size(max = 120) String client,
            @Schema(description = "OPEN = ouvert à la saisie, CLOSED = clos (plus de saisie possible). OPEN par défaut", example = "OPEN", allowableValues = {"OPEN", "CLOSED"})
            @Pattern(regexp = "OPEN|CLOSED") String status) {
    }

    @Schema(description = "Projet")
    public record ProjectDto(UUID id, String code, String name, String client, String status) {
    }

    @Schema(description = "Temps passé sur un projet un jour donné")
    public record EntryRequest(
            @Schema(description = "Projet ouvert", example = "3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
            @NotNull UUID projectId,
            @Schema(description = "Jour (AAAA-MM-JJ), dans la semaine concernée", example = "2026-11-23")
            @NotNull LocalDate date,
            @Schema(description = "Heures, en quarts d'heure (0,25), de 0,25 à 10 ; 10 h maximum par jour tous projets confondus", example = "7.5")
            @NotNull @DecimalMin("0.25") @DecimalMax("10") BigDecimal hours) {
    }

    @Schema(description = "Saisie complète d'une semaine (remplace les lignes existantes)")
    public record WeekRequest(
            @Schema(description = "Lignes de temps (une par projet et par jour)")
            @NotNull @Size(max = 200) List<@Valid EntryRequest> entries) {
    }

    @Schema(description = "Renvoi d'une semaine au salarié")
    public record ReturnRequest(
            @Schema(description = "Motif du renvoi, obligatoire, 500 caractères maximum", example = "Il manque le mercredi après-midi")
            @NotBlank @Size(max = 500) String comment) {
    }

    @Schema(description = "Ligne de la grille : un projet et ses heures par jour")
    public record RowDto(UUID projectId, String projectCode, String projectName, Map<LocalDate, BigDecimal> hours, BigDecimal total) {
    }

    @Schema(description = "Semaine de saisie")
    public record WeekDto(String week, LocalDate monday, UUID userId, String userName, WeekStatus status, String comment,
                          List<RowDto> rows, Map<LocalDate, BigDecimal> dayTotals, BigDecimal total,
                          List<LocalDate> missingDays, boolean editable, Instant submittedAt, String reviewedByName) {
    }

    @Schema(description = "Semaine dans une liste")
    public record WeekSummaryDto(String week, UUID userId, String userName, WeekStatus status, BigDecimal total) {
    }

    @Schema(description = "Ligne de rapport : heures par projet et par personne")
    public record ReportRowDto(String projectCode, String projectName, UUID userId, String userName, BigDecimal hours) {
    }
}
