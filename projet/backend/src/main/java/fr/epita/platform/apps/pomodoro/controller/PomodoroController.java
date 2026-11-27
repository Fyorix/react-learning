package fr.epita.platform.apps.pomodoro.controller;

import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.InviteRequest;
import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.RoomRequest;
import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.RoomSummaryDto;
import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.StateDto;
import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.StatsDto;
import fr.epita.platform.apps.pomodoro.service.PomodoroService;
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

@Tag(name = "Pomodoro", description = "Sessions de focus synchronisées entre les membres d'une équipe.")
@RestController
@RequestMapping("/api/pomodoro")
@RequiredArgsConstructor
public class PomodoroController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String MEMBER = "Réservé aux membres de la salle (FORBIDDEN)";
    private static final String HOST = "Réservé à l'hôte de la salle (FORBIDDEN)";
    private static final String NF = "Salle introuvable (NOT_FOUND)";

    private final PomodoroService service;
    private final UserRepository users;

    @GetMapping("/rooms")
    @Operation(summary = "Mes salles", description = "Accès : authentifié. Salles dont l'utilisateur est membre (l'hôte en est membre).")
    @ApiResponse(responseCode = "200", description = "Salles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<RoomSummaryDto> rooms(@AuthenticationPrincipal AuthUser me) {
        return service.myRooms(me);
    }

    @PostMapping("/rooms")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une salle", description = "Accès : authentifié (devient hôte). Durées par défaut : 25 / 5 / 15 min, pause longue tous les 4 focus.")
    @ApiResponse(responseCode = "201", description = "Salle créée (IDLE)")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public StateDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody RoomRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/rooms/{id}")
    @Operation(summary = "Modifier les réglages", description = "Accès : hôte. Seulement quand la salle est IDLE.")
    @ApiResponse(responseCode = "200", description = "État")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = HOST)
    @ApiResponse(responseCode = "404", description = NF)
    @ApiResponse(responseCode = "409", description = "Session en cours (SESSION_RUNNING)")
    public StateDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RoomRequest req) {
        return service.update(me, id, req);
    }

    @PostMapping("/rooms/{id}/members")
    @Operation(summary = "Inviter des membres", description = "Accès : hôte. Les membres déjà présents sont ignorés.")
    @ApiResponse(responseCode = "200", description = "État")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = HOST)
    @ApiResponse(responseCode = "404", description = "Salle ou utilisateur introuvable (NOT_FOUND)")
    public StateDto invite(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody InviteRequest req) {
        return service.invite(me, id, req.userIds());
    }

    @GetMapping("/rooms/{id}/state")
    @Operation(summary = "État de la session", description = "Accès : membres. Phase, `phaseEndsAt`, `serverTime` (pour corriger le décalage d'horloge), cycle, membres et présence. À interroger toutes les 2 s. Les phases terminées sont rattrapées à la lecture.")
    @ApiResponse(responseCode = "200", description = "État")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MEMBER)
    @ApiResponse(responseCode = "404", description = NF)
    public StateDto state(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.getState(me, id);
    }

    @PostMapping("/rooms/{id}/start")
    @Operation(summary = "Démarrer un focus", description = "Accès : hôte. Salle IDLE uniquement.")
    @ApiResponse(responseCode = "200", description = "État (FOCUS)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = HOST)
    @ApiResponse(responseCode = "404", description = NF)
    @ApiResponse(responseCode = "409", description = "Session déjà démarrée (SESSION_RUNNING)")
    public StateDto start(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.start(me, id);
    }

    @PostMapping("/rooms/{id}/pause")
    @Operation(summary = "Mettre en pause", description = "Accès : hôte. Le temps restant est figé.")
    @ApiResponse(responseCode = "200", description = "État (PAUSED)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = HOST)
    @ApiResponse(responseCode = "404", description = NF)
    @ApiResponse(responseCode = "409", description = "Aucune phase en cours (INVALID_STATUS_TRANSITION)")
    public StateDto pause(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.pause(me, id);
    }

    @PostMapping("/rooms/{id}/resume")
    @Operation(summary = "Reprendre", description = "Accès : hôte. Repart avec le temps restant figé.")
    @ApiResponse(responseCode = "200", description = "État")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = HOST)
    @ApiResponse(responseCode = "404", description = NF)
    @ApiResponse(responseCode = "409", description = "Session non en pause (INVALID_STATUS_TRANSITION)")
    public StateDto resume(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.resume(me, id);
    }

    @PostMapping("/rooms/{id}/skip")
    @Operation(summary = "Passer à la phase suivante", description = "Accès : hôte. Un focus interrompu n'est pas compté.")
    @ApiResponse(responseCode = "200", description = "État")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = HOST)
    @ApiResponse(responseCode = "404", description = NF)
    @ApiResponse(responseCode = "409", description = "Aucune phase en cours (INVALID_STATUS_TRANSITION)")
    public StateDto skip(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.skip(me, id);
    }

    @PostMapping("/rooms/{id}/stop")
    @Operation(summary = "Arrêter la session", description = "Accès : hôte. La salle repasse en IDLE, cycle remis à zéro.")
    @ApiResponse(responseCode = "200", description = "État (IDLE)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = HOST)
    @ApiResponse(responseCode = "404", description = NF)
    public StateDto stop(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.stop(me, id);
    }

    @PostMapping("/rooms/{id}/heartbeat")
    @Operation(summary = "Signaler sa présence", description = "Accès : membres. À appeler toutes les 30 s ; sans signal depuis 60 s, le membre est AWAY. Une salle sans membre présent pendant 10 min repasse en IDLE.")
    @ApiResponse(responseCode = "200", description = "État")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MEMBER)
    @ApiResponse(responseCode = "404", description = NF)
    public StateDto heartbeat(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.heartbeat(me, id);
    }

    @GetMapping("/me/stats")
    @Operation(summary = "Mes statistiques", description = "Accès : authentifié. Pomodoros terminés par jour sur `period` = week (7 jours) ou month (30 jours). Un pomodoro compte si l'on était présent pendant tout le focus.")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "400", description = "Période inconnue (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    public StatsDto stats(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String period) {
        return service.stats(me, period);
    }

    @GetMapping("/candidates")
    @Operation(summary = "Membres possibles", description = "Accès : authentifié. Utilisateurs actifs pour inviter dans une salle.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> candidates() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
