package fr.epita.platform.apps.goals.controller;

import fr.epita.platform.apps.goals.dto.GoalDtos.AssignManagerRequest;
import fr.epita.platform.apps.goals.dto.GoalDtos.EmployeeStatsDto;
import fr.epita.platform.apps.goals.dto.GoalDtos.GoalDto;
import fr.epita.platform.apps.goals.dto.GoalDtos.GoalRequest;
import fr.epita.platform.apps.goals.dto.GoalDtos.ProgressRequest;
import fr.epita.platform.apps.goals.dto.GoalDtos.ReportDto;
import fr.epita.platform.apps.goals.dto.GoalDtos.ReviewRequest;
import fr.epita.platform.apps.goals.entity.GoalStatus;
import fr.epita.platform.apps.goals.service.GoalService;
import fr.epita.platform.common.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Objectifs", description = "Objectifs SMART fixés par le manager, avancement saisi par le salarié, revue par le manager.")
@RestController
@RequestMapping("/api/goals")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService service;

    @Operation(summary = "Mes objectifs", description = "Accès : authentifié (salarié). Uniquement ses propres objectifs, filtre facultatif `status`.")
    @ApiResponse(responseCode = "200", description = "Objectifs")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/me")
    public List<GoalDto> mine(@AuthenticationPrincipal AuthUser me,
                              @RequestParam(required = false) GoalStatus status) {
        return service.mine(me, status);
    }

    @Operation(summary = "Objectifs de mon équipe", description = "Accès : manager. Uniquement les objectifs de ses salariés ; filtres `employeeId` et `status`.")
    @ApiResponse(responseCode = "200", description = "Objectifs")
    @ApiResponse(responseCode = "400", description = "Paramètre invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Pas le manager du salarié ni ADMIN (FORBIDDEN)")
    @GetMapping("/team")
    public List<GoalDto> team(@AuthenticationPrincipal AuthUser me,
                              @RequestParam(required = false) UUID employeeId,
                              @RequestParam(required = false) GoalStatus status) {
        return service.team(me, employeeId, status);
    }

    @Operation(summary = "Statistiques de mon équipe", description = "Accès : manager. Taux de complétion et de validation par salarié.")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/team/stats")
    public List<EmployeeStatsDto> teamStats(@AuthenticationPrincipal AuthUser me) {
        return service.teamStats(me);
    }

    @Operation(summary = "Détail d'un objectif", description = "Accès : le salarié concerné, son manager ou un ADMIN. Contient l'historique des avancements.")
    @ApiResponse(responseCode = "200", description = "Objectif")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Objectif non accessible (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Objectif introuvable (NOT_FOUND)")
    @GetMapping("/{id}")
    public GoalDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @Operation(summary = "Créer un objectif", description = "Accès : manager du salarié ou ADMIN. Contrôles SMART : titre 5 à 120 caractères et description (Spécifique) ; indicateur, unité, valeur de départ et cible différentes (Mesurable) ; 5 objectifs en cours maximum par salarié (Atteignable, 409 TOO_MANY_GOALS) ; un seul salarié (Individuel) ; échéance dans le futur et à 12 mois maximum (Temporel).")
    @ApiResponse(responseCode = "201", description = "Objectif créé")
    @ApiResponse(responseCode = "400", description = "Critère SMART non respecté : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Pas le manager du salarié ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Salarié introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Trop d'objectifs en cours (TOO_MANY_GOALS)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody GoalRequest req) {
        return service.create(me, req);
    }

    @Operation(summary = "Modifier un objectif", description = "Accès : manager du salarié ou ADMIN. Modifiable librement tant qu'aucun avancement n'est enregistré ; ensuite seule l'échéance peut être repoussée. Contrôles SMART : titre 5 à 120 caractères et description (Spécifique) ; indicateur, unité, valeur de départ et cible différentes (Mesurable) ; 5 objectifs en cours maximum par salarié (Atteignable, 409 TOO_MANY_GOALS) ; un seul salarié (Individuel) ; échéance dans le futur et à 12 mois maximum (Temporel).")
    @ApiResponse(responseCode = "200", description = "Objectif modifié")
    @ApiResponse(responseCode = "400", description = "Critère SMART non respecté (VALIDATION_ERROR) ou champ verrouillé (GOAL_LOCKED)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Pas le manager du salarié ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Objectif introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "GOAL_LOCKED, GOAL_CLOSED ou TOO_MANY_GOALS")
    @PutMapping("/{id}")
    public GoalDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                          @Valid @RequestBody GoalRequest req) {
        return service.update(me, id, req);
    }

    @Operation(summary = "Supprimer un objectif", description = "Accès : manager du salarié ou ADMIN, uniquement sans avancement. Le salarié ne peut jamais supprimer ses objectifs.")
    @ApiResponse(responseCode = "204", description = "Objectif supprimé")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Pas le manager du salarié ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Objectif introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Avancement déjà enregistré (GOAL_LOCKED)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @Operation(summary = "Enregistrer un avancement", description = "Accès : le salarié concerné, objectif IN_PROGRESS et échéance non dépassée. Chaque avancement est historisé ; la dernière valeur devient `currentValue`.")
    @ApiResponse(responseCode = "200", description = "Objectif mis à jour")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Seul le salarié concerné saisit un avancement (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Objectif introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Objectif clos ou échu (GOAL_CLOSED)")
    @PostMapping("/{id}/progress")
    public GoalDto progress(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                            @Valid @RequestBody ProgressRequest req) {
        return service.recordProgress(me, id, req.value(), req.comment());
    }

    @Operation(summary = "Déclarer l'objectif terminé", description = "Accès : le salarié concerné. IN_PROGRESS → SUBMITTED.")
    @ApiResponse(responseCode = "200", description = "Objectif soumis")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Seul le salarié concerné peut soumettre (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Objectif introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Objectif déjà soumis ou clos (GOAL_CLOSED)")
    @PostMapping("/{id}/submit")
    public GoalDto submit(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.submit(me, id);
    }

    @Operation(summary = "Revue d'un objectif", description = "Accès : manager du salarié ou ADMIN, objectif SUBMITTED. VALIDATED, NOT_VALIDATED (commentaire obligatoire) ou REOPEN (retour en IN_PROGRESS).")
    @ApiResponse(responseCode = "200", description = "Objectif revu")
    @ApiResponse(responseCode = "400", description = "Commentaire manquant pour NOT_VALIDATED (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Pas le manager du salarié ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Objectif introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Objectif non soumis (INVALID_STATUS_TRANSITION) ou REOPEN après l'échéance (GOAL_CLOSED)")
    @PostMapping("/{id}/review")
    public GoalDto review(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                          @Valid @RequestBody ReviewRequest req) {
        return service.review(me, id, req);
    }

    @Operation(summary = "Organigramme", description = "Accès : ADMIN. Relations manager → salariés.")
    @ApiResponse(responseCode = "200", description = "Relations")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @GetMapping("/admin/reports")
    public List<ReportDto> reports() {
        return service.reports();
    }

    @Operation(summary = "Affecter un manager", description = "Accès : ADMIN. Un salarié a au plus un manager (remplacé s'il existe) ; pas son propre manager ; pas de cycle.")
    @ApiResponse(responseCode = "200", description = "Relation enregistrée")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "SELF_MANAGER ou REPORTING_CYCLE")
    @PutMapping("/admin/reports/{employeeId}")
    public ReportDto assign(@PathVariable UUID employeeId, @Valid @RequestBody AssignManagerRequest req) {
        return service.assignManager(employeeId, req.managerId());
    }

    @Operation(summary = "Retirer le manager", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "204", description = "Relation supprimée")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Relation introuvable (NOT_FOUND)")
    @DeleteMapping("/admin/reports/{employeeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeManager(@PathVariable UUID employeeId) {
        service.removeManager(employeeId);
    }
}
