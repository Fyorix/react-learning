package fr.epita.platform.apps.enps.controller;

import fr.epita.platform.apps.enps.dto.EnpsDtos.*;
import fr.epita.platform.apps.enps.service.EnpsService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.user.dto.UserDtos.UserSummary;
import fr.epita.platform.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "eNPS", description = "Enquêtes de satisfaction anonymes, résultats par équipe (5 réponses minimum) et tendances.")
@RestController
@RequestMapping("/api/enps")
@RequiredArgsConstructor
public class EnpsController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String S404 = "Enquête introuvable (NOT_FOUND)";

    private final EnpsService service;
    private final UserRepository users;

    @GetMapping("/teams")
    @Operation(summary = "Équipes", description = "Accès : authentifié. Équipes et membres (pour filtrer les résultats).")
    @ApiResponse(responseCode = "200", description = "Équipes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<TeamDto> teams() {
        return service.teams();
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une équipe", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "201", description = "Équipe créée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Nom déjà pris (TEAM_EXISTS)")
    public TeamDto createTeam(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TeamRequest req) {
        return service.createTeam(me, req);
    }

    @PutMapping("/teams/{id}/members")
    @Operation(summary = "Membres d'une équipe", description = "Accès : ADMIN. Remplace la liste ; un salarié appartient à une seule équipe (il quitte l'ancienne).")
    @ApiResponse(responseCode = "200", description = "Équipe")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Équipe ou utilisateur introuvable (NOT_FOUND)")
    public TeamDto members(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody MembersRequest req) {
        return service.setMembers(me, id, req);
    }

    @PostMapping("/surveys")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une enquête", description = "Accès : ADMIN. La question eNPS (0 à 10) est ajoutée en première position ; questions complémentaires facultatives.")
    @ApiResponse(responseCode = "201", description = "Enquête créée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public SurveyDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SurveyRequest req) {
        return service.create(me, req);
    }

    @GetMapping("/surveys")
    @Operation(summary = "Enquêtes", description = "Accès : authentifié. Salarié : enquêtes ouvertes ou closes, avec l'indicateur « déjà répondu » ; ADMIN : toutes.")
    @ApiResponse(responseCode = "200", description = "Enquêtes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<SurveyDto> list(@AuthenticationPrincipal AuthUser me) {
        return service.list(me);
    }

    @GetMapping("/surveys/{id}")
    @Operation(summary = "Questions d'une enquête", description = "Accès : authentifié (enquête ouverte ou close ; à venir : ADMIN).")
    @ApiResponse(responseCode = "200", description = "Enquête")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = S404)
    public SurveyDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/surveys/{id}/responses")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Répondre", description = "Accès : authentifié. Anonyme : seule l'équipe est conservée avec la réponse. Une réponse par personne, non modifiable, pendant la période d'ouverture.")
    @ApiResponse(responseCode = "204", description = "Réponse enregistrée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = S404)
    @ApiResponse(responseCode = "409", description = "ALREADY_ANSWERED ou SURVEY_CLOSED")
    public void respond(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ResponseRequest req) {
        service.respond(me, id, req);
    }

    @GetMapping("/surveys/{id}/results")
    @Operation(summary = "Résultats", description = "Accès : ADMIN à tout moment, salariés après la clôture. Global et par équipe (filtre `teamId`) ; sous 5 réponses : `insufficientResponses`. Verbatims mélangés, sans date.")
    @ApiResponse(responseCode = "200", description = "Résultats")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Enquête pas encore close (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = S404)
    public ResultsDto results(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @RequestParam(required = false) UUID teamId) {
        return service.results(me, id, teamId);
    }

    @GetMapping("/trends")
    @Operation(summary = "Tendance de l'eNPS", description = "Accès : authentifié. eNPS par enquête (close ; ouverte aussi pour l'ADMIN), global ou d'une équipe ; null sous 5 réponses.")
    @ApiResponse(responseCode = "200", description = "Tendance")
    @ApiResponse(responseCode = "401", description = U401)
    public List<TrendPoint> trends(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) UUID teamId) {
        return service.trends(me, teamId);
    }

    @GetMapping("/surveys/{id}/non-respondents")
    @Operation(summary = "Personnes à relancer", description = "Accès : ADMIN. Salariés actifs n'ayant pas répondu ; aucune information sur le contenu des réponses.")
    @ApiResponse(responseCode = "200", description = "Non-répondants")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = S404)
    public List<NonRespondentDto> nonRespondents(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.nonRespondents(me, id);
    }

    @GetMapping("/people")
    @Operation(summary = "Salariés", description = "Accès : authentifié. Utilisateurs actifs, pour composer les équipes.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
