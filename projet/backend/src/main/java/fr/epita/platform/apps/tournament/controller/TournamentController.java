package fr.epita.platform.apps.tournament.controller;

import fr.epita.platform.apps.tournament.dto.TournamentDtos.*;
import fr.epita.platform.apps.tournament.entity.TournamentStatus;
import fr.epita.platform.apps.tournament.service.TournamentService;
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

@Tag(name = "Tournoi", description = "Tournois de baby-foot : inscriptions, tableau ou poule, scores confirmés par l'adversaire, classement Elo.")
@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String T404 = "Tournoi introuvable (NOT_FOUND)";
    private static final String M404 = "Match introuvable (NOT_FOUND)";
    private static final String NOT_PLAYER = "Vous ne jouez pas ce match (FORBIDDEN)";

    private final TournamentService service;
    private final UserRepository users;

    @GetMapping
    @Operation(summary = "Tournois", description = "Accès : authentifié. Filtre facultatif par statut.")
    @ApiResponse(responseCode = "200", description = "Tournois")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<TournamentSummary> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) TournamentStatus status) {
        return service.list(me, status);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un tournoi", description = "Accès : authentifié (devient l'organisateur). Fin des inscriptions dans le futur.")
    @ApiResponse(responseCode = "201", description = "Tournoi créé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public TournamentDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TournamentRequest req) {
        return service.create(me, req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un tournoi", description = "Accès : authentifié. Participants (têtes de série), matchs par tour, classement de poule, vainqueur.")
    @ApiResponse(responseCode = "200", description = "Tournoi")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = T404)
    public TournamentDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "S'inscrire", description = "Accès : authentifié. En double, `partnerId` obligatoire et coéquipier non inscrit ; avant la fin des inscriptions.")
    @ApiResponse(responseCode = "201", description = "Tournoi")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Tournoi ou coéquipier introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "REGISTRATION_CLOSED, ALREADY_REGISTERED ou PARTNER_ALREADY_REGISTERED")
    public TournamentDto register(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                  @Valid @RequestBody(required = false) RegistrationRequest req) {
        return service.register(me, id, req == null ? null : req.partnerId());
    }

    @DeleteMapping("/{id}/registrations/me")
    @Operation(summary = "Se désinscrire", description = "Accès : inscrit (lui ou son équipe), avant la génération du tableau.")
    @ApiResponse(responseCode = "200", description = "Tournoi")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Tournoi ou inscription introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Tableau déjà généré (REGISTRATION_CLOSED)")
    public TournamentDto unregister(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.unregister(me, id);
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "Générer le tableau", description = "Accès : organisateur. Clôt les inscriptions ; têtes de série par Elo (égalités tirées au sort) ; exempts pour compléter à une puissance de 2 ; en poule, chacun rencontre chacun.")
    @ApiResponse(responseCode = "200", description = "Tournoi en cours")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé à l'organisateur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = T404)
    @ApiResponse(responseCode = "409", description = "NOT_ENOUGH_PLAYERS ou INVALID_STATUS_TRANSITION")
    public TournamentDto start(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.start(me, id);
    }

    @PutMapping("/matches/{matchId}/score")
    @Operation(summary = "Saisir le score", description = "Accès : joueur du match. 10 points gagnants : un seul camp à 10.")
    @ApiResponse(responseCode = "200", description = "Match en attente de confirmation")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = NOT_PLAYER)
    @ApiResponse(responseCode = "404", description = M404)
    @ApiResponse(responseCode = "409", description = "Score déjà saisi ou match non jouable (INVALID_STATUS_TRANSITION)")
    public MatchDto score(@AuthenticationPrincipal AuthUser me, @PathVariable UUID matchId, @Valid @RequestBody ScoreRequest req) {
        return service.report(me, matchId, req);
    }

    @PostMapping("/matches/{matchId}/confirm")
    @Operation(summary = "Confirmer le score", description = "Accès : joueur du camp adverse à celui qui a saisi. Met à jour l'Elo (K = 32) et qualifie le vainqueur.")
    @ApiResponse(responseCode = "200", description = "Match confirmé")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = NOT_PLAYER)
    @ApiResponse(responseCode = "404", description = M404)
    @ApiResponse(responseCode = "409", description = "SCORE_NOT_CONFIRMABLE (son propre camp) ou INVALID_STATUS_TRANSITION")
    public MatchDto confirm(@AuthenticationPrincipal AuthUser me, @PathVariable UUID matchId) {
        return service.confirm(me, matchId);
    }

    @PostMapping("/matches/{matchId}/dispute")
    @Operation(summary = "Contester le score", description = "Accès : joueur du camp adverse. Le match redevient à saisir.")
    @ApiResponse(responseCode = "200", description = "Match à saisir")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = NOT_PLAYER)
    @ApiResponse(responseCode = "404", description = M404)
    @ApiResponse(responseCode = "409", description = "SCORE_NOT_CONFIRMABLE ou INVALID_STATUS_TRANSITION")
    public MatchDto dispute(@AuthenticationPrincipal AuthUser me, @PathVariable UUID matchId) {
        return service.dispute(me, matchId);
    }

    @GetMapping("/me/matches")
    @Operation(summary = "Mes matchs", description = "Accès : authentifié. D'abord ceux à saisir ou à confirmer.")
    @ApiResponse(responseCode = "200", description = "Matchs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<MatchDto> myMatches(@AuthenticationPrincipal AuthUser me) {
        return service.myMatches(me);
    }

    @GetMapping("/ranking")
    @Operation(summary = "Classement Elo", description = "Accès : authentifié. Joueurs ayant au moins un match confirmé.")
    @ApiResponse(responseCode = "200", description = "Classement")
    @ApiResponse(responseCode = "401", description = U401)
    public List<RankingRow> ranking() {
        return service.ranking();
    }

    @GetMapping("/players/{userId}")
    @Operation(summary = "Profil joueur", description = "Accès : authentifié. Elo (1000 au départ) et historique des matchs confirmés.")
    @ApiResponse(responseCode = "200", description = "Profil")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    public PlayerDto player(@AuthenticationPrincipal AuthUser me, @PathVariable UUID userId) {
        return service.player(me, userId);
    }

    @GetMapping("/players")
    @Operation(summary = "Joueurs", description = "Accès : authentifié. Utilisateurs actifs, pour choisir un coéquipier.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> players() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
