package fr.epita.platform.apps.meeting.controller;

import fr.epita.platform.apps.meeting.dto.MeetingDtos.*;
import fr.epita.platform.apps.meeting.entity.PollStatus;
import fr.epita.platform.apps.meeting.service.MeetingService;
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

@Tag(name = "Planificateur de réunions", description = "Propositions de créneaux, disponibilités des participants, décision de l'organisateur.")
@RestController
@RequestMapping("/api/meeting-polls")
@RequiredArgsConstructor
public class MeetingController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String P404 = "Proposition introuvable (NOT_FOUND)";
    private static final String ORG = "Réservé à l'organisateur (FORBIDDEN)";

    private final MeetingService service;
    private final UserRepository users;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une proposition", description = "Accès : authentifié (devient organisateur et participant). 2 à 20 créneaux futurs, sans doublon.")
    @ApiResponse(responseCode = "201", description = "Proposition")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Participant introuvable (NOT_FOUND)")
    public PollDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PollRequest req) {
        return service.create(me, req);
    }

    @GetMapping
    @Operation(summary = "Mes propositions", description = "Accès : authentifié. `role=organizer` (les miennes), `participant` (où je suis invité), sans filtre : toutes ; filtre `status`.")
    @ApiResponse(responseCode = "200", description = "Propositions")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public List<PollSummary> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String role,
                                  @RequestParam(required = false) PollStatus status) {
        return service.list(me, role, status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail", description = "Accès : participants. Créneaux et décomptes, réponses de chacun, créneau suggéré.")
    @ApiResponse(responseCode = "200", description = "Proposition")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas invité (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = P404)
    public PollDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier", description = "Accès : organisateur, tant que personne n'a répondu.")
    @ApiResponse(responseCode = "200", description = "Proposition")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ORG)
    @ApiResponse(responseCode = "404", description = P404)
    @ApiResponse(responseCode = "409", description = "POLL_HAS_ANSWERS ou POLL_DECIDED")
    public PollDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody PollRequest req) {
        return service.update(me, id, req);
    }

    @PutMapping("/{id}/answers/me")
    @Operation(summary = "Répondre", description = "Accès : participants invités. Remplace mes réponses (YES, IF_NEEDED, NO) ; modifiable jusqu'à la décision.")
    @ApiResponse(responseCode = "200", description = "Proposition")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas invité (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = P404)
    @ApiResponse(responseCode = "409", description = "Réunion fixée (POLL_DECIDED)")
    public PollDto answer(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody AnswersRequest req) {
        return service.answer(me, id, req.answers());
    }

    @PostMapping("/{id}/decide")
    @Operation(summary = "Retenir un créneau", description = "Accès : organisateur. Passe la proposition en DECIDED et fige les réponses.")
    @ApiResponse(responseCode = "200", description = "Proposition")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ORG)
    @ApiResponse(responseCode = "404", description = "Proposition ou créneau introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "POLL_DECIDED ou SLOT_IN_PAST")
    public PollDto decide(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody DecideRequest req) {
        return service.decide(me, id, req.slotId());
    }

    @PostMapping("/{id}/remind")
    @Operation(summary = "Relancer les non-répondants", description = "Accès : organisateur. Une relance par 24 h au plus.")
    @ApiResponse(responseCode = "200", description = "Participants relancés")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ORG)
    @ApiResponse(responseCode = "404", description = P404)
    @ApiResponse(responseCode = "409", description = "REMINDER_TOO_SOON, NOTHING_TO_REMIND ou POLL_DECIDED")
    public ReminderDto remind(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.remind(me, id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer", description = "Accès : organisateur.")
    @ApiResponse(responseCode = "204", description = "Supprimée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ORG)
    @ApiResponse(responseCode = "404", description = P404)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @GetMapping("/people")
    @Operation(summary = "Utilisateurs", description = "Accès : authentifié. Utilisateurs actifs à inviter.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
