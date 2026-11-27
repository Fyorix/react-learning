package fr.epita.platform.apps.retro.controller;

import fr.epita.platform.apps.retro.dto.RetroDtos.*;
import fr.epita.platform.apps.retro.service.RetroService;
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

@Tag(name = "Rétrospective", description = "Rétrospectives d'équipe : phases, timer, cartes masquées pendant l'écriture, votes et actions.")
@RestController
@RequestMapping("/api/retros")
@RequiredArgsConstructor
public class RetroController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String R404 = "Rétrospective introuvable ou réservée aux membres de l'équipe (NOT_FOUND)";
    private static final String FAC = "Réservé à l'animateur (FORBIDDEN)";
    private static final String LOCKED = "Action impossible dans la phase actuelle (PHASE_LOCKED)";

    private final RetroService service;
    private final UserRepository users;

    @GetMapping("/teams")
    @Operation(summary = "Mes équipes", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Équipes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<TeamDto> teams(@AuthenticationPrincipal AuthUser me) {
        return service.myTeams(me);
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une équipe", description = "Accès : authentifié. Le créateur en est membre d'office.")
    @ApiResponse(responseCode = "201", description = "Équipe")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    public TeamDto createTeam(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TeamRequest req) {
        return service.createTeam(me, req);
    }

    @GetMapping
    @Operation(summary = "Rétrospectives", description = "Accès : membres. De toutes mes équipes, ou d'une équipe (`teamId`).")
    @ApiResponse(responseCode = "200", description = "Rétrospectives")
    @ApiResponse(responseCode = "401", description = U401)
    public List<RetroSummary> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) UUID teamId) {
        return service.list(me, teamId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une rétrospective", description = "Accès : membre de l'équipe, qui devient l'animateur. Démarre en WRITING.")
    @ApiResponse(responseCode = "201", description = "Rétrospective")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Équipe introuvable ou dont je ne suis pas membre (NOT_FOUND)")
    public RetroDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody RetroRequest req) {
        return service.create(me, req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "État complet", description = "Accès : membres. Phase, timer (`timerEndsAt` ou `timerRemaining`) et `serverTime`, cartes visibles selon la phase, groupes, votes, actions.")
    @ApiResponse(responseCode = "200", description = "État")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = R404)
    public RetroDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/{id}/phase")
    @Operation(summary = "Phase suivante", description = "Accès : animateur. WRITING → GROUPING → VOTING → DISCUSSION → ACTIONS → CLOSED, uniquement vers la suivante.")
    @ApiResponse(responseCode = "200", description = "État")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = FAC)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "Déjà close (INVALID_PHASE_TRANSITION)")
    public RetroDto phase(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.nextPhase(me, id);
    }

    @PutMapping("/{id}/timer")
    @Operation(summary = "Piloter le timer", description = "Accès : animateur. START (durée), PAUSE (temps restant figé), RESUME, ADD (secondes). La fin du timer ne change pas de phase.")
    @ApiResponse(responseCode = "200", description = "État")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = FAC)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "TIMER_NOT_RUNNING, TIMER_NOT_PAUSED ou PHASE_LOCKED")
    public RetroDto timer(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody TimerRequest req) {
        return service.timer(me, id, req);
    }

    @PostMapping("/{id}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une carte", description = "Accès : membres, phase WRITING.")
    @ApiResponse(responseCode = "201", description = "État")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = LOCKED)
    public RetroDto addCard(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody CardRequest req) {
        return service.addCard(me, id, req);
    }

    @PutMapping("/{id}/cards/{cardId}")
    @Operation(summary = "Modifier ma carte", description = "Accès : auteur, phase WRITING.")
    @ApiResponse(responseCode = "200", description = "État")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas l'auteur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Rétrospective ou carte introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = LOCKED)
    public RetroDto updateCard(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @PathVariable UUID cardId, @Valid @RequestBody CardRequest req) {
        return service.updateCard(me, id, cardId, req);
    }

    @DeleteMapping("/{id}/cards/{cardId}")
    @Operation(summary = "Supprimer ma carte", description = "Accès : auteur, phase WRITING.")
    @ApiResponse(responseCode = "200", description = "État")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas l'auteur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Rétrospective ou carte introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = LOCKED)
    public RetroDto deleteCard(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @PathVariable UUID cardId) {
        return service.deleteCard(me, id, cardId);
    }

    @PostMapping("/{id}/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Regrouper des cartes", description = "Accès : animateur, phase GROUPING.")
    @ApiResponse(responseCode = "201", description = "État")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = FAC)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = LOCKED)
    public RetroDto group(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody GroupRequest req) {
        return service.group(me, id, req);
    }

    @PutMapping("/{id}/votes/me")
    @Operation(summary = "Mes votes", description = "Accès : membres, phase VOTING. Remplace mes votes : 3 au total, 2 au plus par carte ou groupe ; totaux masqués jusqu'à la fin du vote.")
    @ApiResponse(responseCode = "200", description = "État")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "NO_VOTE_LEFT ou PHASE_LOCKED")
    public RetroDto vote(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody VotesRequest req) {
        return service.vote(me, id, req);
    }

    @PostMapping("/{id}/actions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une action", description = "Accès : membres, phase ACTIONS. Responsable obligatoire, membre de l'équipe.")
    @ApiResponse(responseCode = "201", description = "État")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = LOCKED)
    public RetroDto addAction(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ActionRequest req) {
        return service.addAction(me, id, req);
    }

    @PatchMapping("/actions/{actionId}")
    @Operation(summary = "Marquer une action", description = "Accès : responsable de l'action (ou animateur).")
    @ApiResponse(responseCode = "200", description = "Action")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le responsable (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Action introuvable (NOT_FOUND)")
    public ActionDto done(@AuthenticationPrincipal AuthUser me, @PathVariable UUID actionId, @Valid @RequestBody DoneRequest req) {
        return service.setDone(me, actionId, req.done());
    }

    @GetMapping("/teams/{teamId}/open-actions")
    @Operation(summary = "Actions en cours de l'équipe", description = "Accès : membres. Actions non terminées, toutes rétrospectives confondues.")
    @ApiResponse(responseCode = "200", description = "Actions")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Équipe introuvable (NOT_FOUND)")
    public List<ActionDto> openActions(@AuthenticationPrincipal AuthUser me, @PathVariable UUID teamId) {
        return service.openActions(me, teamId);
    }

    @GetMapping("/people")
    @Operation(summary = "Collègues", description = "Accès : authentifié. Utilisateurs actifs, pour composer une équipe.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
