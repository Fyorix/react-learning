package fr.epita.platform.apps.timesheet.controller;

import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.ProjectDto;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.ProjectRequest;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.ReportRowDto;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.ReturnRequest;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.WeekDto;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.WeekRequest;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.WeekSummaryDto;
import fr.epita.platform.apps.timesheet.entity.WeekStatus;
import fr.epita.platform.apps.timesheet.service.TimesheetService;
import fr.epita.platform.common.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Feuilles de temps", description = "Saisie hebdomadaire du temps par projet et validation par le manager.")
@RestController
@RequestMapping("/api/timesheets")
@RequiredArgsConstructor
public class TimesheetController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String WEEK = "`week` au format ISO AAAA-Www (ex. 2026-W48).";
    private static final String MGR = "Pas le manager du salarié, ou sa propre semaine (FORBIDDEN)";

    private final TimesheetService service;

    @GetMapping("/projects")
    @Operation(summary = "Projets", description = "Accès : authentifié. Filtre `status` (OPEN, CLOSED). Le projet ABSENCE sert à déclarer congés et absences.")
    @ApiResponse(responseCode = "200", description = "Projets")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ProjectDto> projects(@RequestParam(required = false) String status) {
        return service.projects(status);
    }

    @PostMapping("/projects")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un projet", description = "Accès : ADMIN. Code unique.")
    @ApiResponse(responseCode = "201", description = "Projet créé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "409", description = "Code déjà utilisé (PROJECT_CODE_EXISTS)")
    public ProjectDto createProject(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ProjectRequest req) {
        return service.createProject(me, req);
    }

    @PutMapping("/projects/{id}")
    @Operation(summary = "Modifier ou clore un projet", description = "Accès : ADMIN. Un projet clos n'accepte plus de saisie.")
    @ApiResponse(responseCode = "200", description = "Projet modifié")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Projet introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Code déjà utilisé (PROJECT_CODE_EXISTS)")
    public ProjectDto updateProject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ProjectRequest req) {
        return service.updateProject(me, id, req);
    }

    @GetMapping("/weeks/{week}")
    @Operation(summary = "Ma semaine", description = "Accès : authentifié. " + WEEK + " Lignes par projet et par jour, totaux, jours ouvrés manquants.")
    @ApiResponse(responseCode = "200", description = "Semaine (vide si rien n'est saisi)")
    @ApiResponse(responseCode = "400", description = "Semaine invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    public WeekDto get(@AuthenticationPrincipal AuthUser me, @PathVariable String week) {
        return service.get(me, week);
    }

    @PutMapping("/weeks/{week}")
    @Operation(summary = "Enregistrer ma semaine", description = "Accès : authentifié. " + WEEK + " Remplace toutes les lignes. Quarts d'heure, 10 h maximum par jour, jours de la semaine uniquement, projets ouverts. Semaine en brouillon ou renvoyée.")
    @ApiResponse(responseCode = "200", description = "Semaine enregistrée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Projet introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "WEEK_LOCKED ou PROJECT_CLOSED")
    public WeekDto save(@AuthenticationPrincipal AuthUser me, @PathVariable String week, @Valid @RequestBody WeekRequest req) {
        return service.save(me, week, req.entries());
    }

    @PostMapping("/weeks/{week}/submit")
    @Operation(summary = "Soumettre ma semaine", description = "Accès : authentifié. Chaque jour ouvré doit être rempli (absence : projet ABSENCE). La semaine n'est plus modifiable ensuite.")
    @ApiResponse(responseCode = "200", description = "Semaine soumise")
    @ApiResponse(responseCode = "400", description = "Semaine invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "409", description = "WEEK_INCOMPLETE ou WEEK_LOCKED")
    public WeekDto submit(@AuthenticationPrincipal AuthUser me, @PathVariable String week) {
        return service.submit(me, week);
    }

    @GetMapping("/me/missing")
    @Operation(summary = "Semaines non soumises", description = "Accès : authentifié. Les 8 semaines passées non soumises (ou renvoyées).")
    @ApiResponse(responseCode = "200", description = "Semaines (AAAA-Www)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> missing(@AuthenticationPrincipal AuthUser me) {
        return service.missing(me);
    }

    @GetMapping("/team/weeks")
    @Operation(summary = "Semaines de mes salariés", description = "Accès : manager (ADMIN : toutes). `status` : SUBMITTED pour les semaines à valider (par défaut : toutes sauf brouillons).")
    @ApiResponse(responseCode = "200", description = "Semaines")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<WeekSummaryDto> team(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) WeekStatus status) {
        return service.team(me, status);
    }

    @GetMapping("/weeks/{week}/users/{userId}")
    @Operation(summary = "Semaine d'un salarié", description = "Accès : manager du salarié ou ADMIN (ajout au §5.19 : consultation avant validation).")
    @ApiResponse(responseCode = "200", description = "Semaine")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MGR)
    @ApiResponse(responseCode = "404", description = "Semaine introuvable (NOT_FOUND)")
    public WeekDto getFor(@AuthenticationPrincipal AuthUser me, @PathVariable String week, @PathVariable UUID userId) {
        return service.getFor(me, week, userId);
    }

    @PostMapping("/weeks/{week}/users/{userId}/approve")
    @Operation(summary = "Valider une semaine", description = "Accès : manager du salarié ou ADMIN. Semaine SUBMITTED.")
    @ApiResponse(responseCode = "200", description = "Semaine validée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MGR)
    @ApiResponse(responseCode = "404", description = "Semaine introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Semaine non soumise (INVALID_STATUS_TRANSITION)")
    public WeekDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable String week, @PathVariable UUID userId) {
        return service.approve(me, week, userId);
    }

    @PostMapping("/weeks/{week}/users/{userId}/return")
    @Operation(summary = "Renvoyer une semaine", description = "Accès : manager du salarié ou ADMIN. Commentaire obligatoire ; la semaine redevient modifiable.")
    @ApiResponse(responseCode = "200", description = "Semaine renvoyée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MGR)
    @ApiResponse(responseCode = "404", description = "Semaine introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Semaine non soumise (INVALID_STATUS_TRANSITION)")
    public WeekDto sendBack(@AuthenticationPrincipal AuthUser me, @PathVariable String week, @PathVariable UUID userId,
                            @Valid @RequestBody ReturnRequest req) {
        return service.sendBack(me, week, userId, req.comment());
    }

    @GetMapping("/reports")
    @Operation(summary = "Rapport d'heures", description = "Accès : authentifié (soi et ses salariés ; ADMIN : tous). Heures soumises ou validées entre `from` et `to` (mois courant par défaut), filtres `projectId`, `userId`.")
    @ApiResponse(responseCode = "200", description = "Heures par projet et par personne")
    @ApiResponse(responseCode = "400", description = "Période invalide (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ReportRowDto> report(@AuthenticationPrincipal AuthUser me,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                     @RequestParam(required = false) UUID projectId,
                                     @RequestParam(required = false) UUID userId) {
        return service.report(me, from, to, projectId, userId);
    }
}
