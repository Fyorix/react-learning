package fr.epita.platform.apps.oncall.controller;

import fr.epita.platform.apps.oncall.dto.OnCallDtos.*;
import fr.epita.platform.apps.oncall.service.OnCallService;
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

@Tag(name = "Astreintes", description = "Rotation hebdomadaire des astreintes, indisponibilités et échanges validés par le manager.")
@RestController
@RequestMapping("/api/oncall")
@RequiredArgsConstructor
public class OnCallController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String MGR = "Réservé au manager de l'équipe (FORBIDDEN)";
    private static final String T404 = "Équipe introuvable ou dont je ne fais pas partie (NOT_FOUND)";
    private static final String S404 = "Échange introuvable (NOT_FOUND)";

    private final OnCallService service;
    private final UserRepository users;

    @GetMapping("/teams")
    @Operation(summary = "Mes équipes", description = "Accès : authentifié. Équipes dont je suis membre ou manager (toutes pour un ADMIN).")
    @ApiResponse(responseCode = "200", description = "Équipes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<TeamDto> teams(@AuthenticationPrincipal AuthUser me) {
        return service.myTeams(me);
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une équipe", description = "Accès : ADMIN. Au moins 2 membres ; nom unique.")
    @ApiResponse(responseCode = "201", description = "Équipe créée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Nom déjà pris (TEAM_EXISTS)")
    public TeamDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TeamRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/teams/{id}")
    @Operation(summary = "Modifier une équipe", description = "Accès : ADMIN. Membres et manager ; le planning existant est conservé.")
    @ApiResponse(responseCode = "200", description = "Équipe")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Équipe ou utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Nom déjà pris (TEAM_EXISTS)")
    public TeamDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody TeamRequest req) {
        return service.update(me, id, req);
    }

    @GetMapping("/teams/{id}/schedule")
    @Operation(summary = "Planning par semaine", description = "Accès : membre, manager ou ADMIN. `from`/`to` au format ISO AAAA-Www (par défaut : 12 semaines depuis la semaine en cours).")
    @ApiResponse(responseCode = "200", description = "Planning")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = T404)
    public ScheduleDto schedule(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                @RequestParam(required = false) String from, @RequestParam(required = false) String to) {
        return service.schedule(me, id, from, to);
    }

    @PostMapping("/teams/{id}/schedule/generate")
    @Operation(summary = "Générer la rotation", description = "Accès : manager. Une personne par semaine, répartition équitable, pas deux semaines de suite (dérogation possible en équipe de 2), indisponibilités respectées. Semaines existantes conservées sauf `overwrite`.")
    @ApiResponse(responseCode = "200", description = "Planning généré")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MGR)
    @ApiResponse(responseCode = "404", description = T404)
    @ApiResponse(responseCode = "409", description = "WEEK_LOCKED ou NO_ELIGIBLE_MEMBER")
    public ScheduleDto generate(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody GenerateRequest req) {
        return service.generate(me, id, req);
    }

    @PutMapping("/teams/{id}/schedule/{week}")
    @Operation(summary = "Affecter une semaine", description = "Accès : manager. Semaine à venir (AAAA-Www), membre disponible, pas de semaines consécutives.")
    @ApiResponse(responseCode = "200", description = "Semaine")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MGR)
    @ApiResponse(responseCode = "404", description = T404)
    @ApiResponse(responseCode = "409", description = "WEEK_LOCKED, MEMBER_UNAVAILABLE ou CONSECUTIVE_ON_CALL")
    public WeekDto assign(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @PathVariable String week, @Valid @RequestBody AssignRequest req) {
        return service.assign(me, id, week, req.userId());
    }

    @PostMapping("/teams/{id}/unavailabilities")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Déclarer une indisponibilité", description = "Accès : membre. Semaine à venir (lundi) où je ne suis pas déjà d'astreinte.")
    @ApiResponse(responseCode = "201", description = "Indisponibilité enregistrée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux membres (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = T404)
    @ApiResponse(responseCode = "409", description = "WEEK_LOCKED, ALREADY_DECLARED ou MEMBER_UNAVAILABLE (déjà d'astreinte)")
    public void unavailable(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody UnavailabilityRequest req) {
        service.unavailable(me, id, req);
    }

    @GetMapping("/me/shifts")
    @Operation(summary = "Mes astreintes", description = "Accès : authentifié. Mes semaines d'astreinte à partir de la semaine en cours.")
    @ApiResponse(responseCode = "200", description = "Astreintes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<MyShiftDto> myShifts(@AuthenticationPrincipal AuthUser me) {
        return service.myShifts(me);
    }

    @GetMapping("/teams/{id}/stats")
    @Operation(summary = "Astreintes par membre", description = "Accès : membre, manager ou ADMIN. Nombre de semaines sur l'année ISO (par défaut l'année en cours).")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = T404)
    public List<StatDto> stats(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @RequestParam(required = false) Integer year) {
        return service.stats(me, id, year);
    }

    @PostMapping("/swaps")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Demander un échange", description = "Accès : membre titulaire de `myWeek`. Le collègue doit être titulaire de `otherWeek` ; semaines à venir ; règles de disponibilité et de semaines consécutives vérifiées.")
    @ApiResponse(responseCode = "201", description = "Échange demandé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = T404)
    @ApiResponse(responseCode = "409", description = "WEEK_LOCKED, NOT_ON_CALL, MEMBER_UNAVAILABLE ou CONSECUTIVE_ON_CALL")
    public SwapDto requestSwap(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SwapRequest req) {
        return service.requestSwap(me, req);
    }

    @GetMapping("/swaps")
    @Operation(summary = "Échanges", description = "Accès : authentifié. `box` = sent (envoyés), received (reçus, par défaut) ou to-approve (à valider, manager).")
    @ApiResponse(responseCode = "200", description = "Échanges")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public List<SwapDto> swaps(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String box) {
        return service.list(me, box);
    }

    @PostMapping("/swaps/{id}/accept")
    @Operation(summary = "Accepter un échange", description = "Accès : collègue sollicité. L'échange attend ensuite le manager.")
    @ApiResponse(responseCode = "200", description = "Échange accepté")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé au collègue sollicité (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = S404)
    @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION, WEEK_LOCKED, NOT_ON_CALL, CONSECUTIVE_ON_CALL")
    public SwapDto accept(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.accept(me, id);
    }

    @PostMapping("/swaps/{id}/approve")
    @Operation(summary = "Valider un échange", description = "Accès : manager. Les deux semaines sont échangées (effectif seulement après les deux accords).")
    @ApiResponse(responseCode = "200", description = "Échange effectif")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MGR)
    @ApiResponse(responseCode = "404", description = S404)
    @ApiResponse(responseCode = "409", description = "INVALID_STATUS_TRANSITION, WEEK_LOCKED, NOT_ON_CALL, CONSECUTIVE_ON_CALL")
    public SwapDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.approve(me, id);
    }

    @PostMapping("/swaps/{id}/reject")
    @Operation(summary = "Refuser un échange", description = "Accès : collègue sollicité (avant acceptation) ou manager.")
    @ApiResponse(responseCode = "200", description = "Échange refusé")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Ni le collègue sollicité ni le manager (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = S404)
    @ApiResponse(responseCode = "409", description = "Déjà validé ou refusé (INVALID_STATUS_TRANSITION)")
    public SwapDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.reject(me, id);
    }

    @GetMapping("/people")
    @Operation(summary = "Utilisateurs", description = "Accès : authentifié. Utilisateurs actifs, pour composer les équipes.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
