package fr.epita.platform.apps.budget.controller;

import fr.epita.platform.apps.budget.dto.BudgetDtos.*;
import fr.epita.platform.apps.budget.entity.PurchaseStatus;
import fr.epita.platform.apps.budget.service.BudgetService;
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

@Tag(name = "Budget d'équipe", description = "Enveloppes annuelles par catégorie, demandes d'achat, engagements, dépenses et alertes.")
@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String MGR = "Réservé au manager de l'équipe ou à un ADMIN (FORBIDDEN)";
    private static final String R404 = "Demande introuvable (NOT_FOUND)";

    private final BudgetService service;
    private final UserRepository users;

    @GetMapping("/teams")
    @Operation(summary = "Mes équipes", description = "Accès : authentifié. Équipes dont je suis membre ou manager (toutes pour un ADMIN).")
    @ApiResponse(responseCode = "200", description = "Équipes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<TeamDto> teams(@AuthenticationPrincipal AuthUser me) {
        return service.teams(me);
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une équipe", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "201", description = "Équipe")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Nom déjà pris (TEAM_EXISTS)")
    public TeamDto createTeam(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TeamRequest req) {
        return service.createTeam(me, req);
    }

    @PutMapping("/teams/{teamId}")
    @Operation(summary = "Modifier une équipe", description = "Accès : ADMIN. Manager et membres.")
    @ApiResponse(responseCode = "200", description = "Équipe")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Équipe ou utilisateur introuvable (NOT_FOUND)")
    public TeamDto updateTeam(@AuthenticationPrincipal AuthUser me, @PathVariable UUID teamId, @Valid @RequestBody TeamRequest req) {
        return service.updateTeam(me, teamId, req);
    }

    @GetMapping("/teams/{teamId}")
    @Operation(summary = "Tableau de bord", description = "Accès : membres, manager, ADMIN. Prévu, engagé, dépensé, restant et consommation par catégorie (année en cours par défaut).")
    @ApiResponse(responseCode = "200", description = "Tableau de bord")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Équipe introuvable ou non accessible (NOT_FOUND)")
    public DashboardDto dashboard(@AuthenticationPrincipal AuthUser me, @PathVariable UUID teamId, @RequestParam(required = false) Integer year) {
        return service.dashboard(me, teamId, year);
    }

    @PutMapping("/teams/{teamId}/envelopes")
    @Operation(summary = "Fixer les enveloppes", description = "Accès : ADMIN. Par catégorie pour un exercice ouvert (créé si besoin).")
    @ApiResponse(responseCode = "200", description = "Tableau de bord")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Équipe introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Exercice clos (FISCAL_YEAR_CLOSED)")
    public DashboardDto envelopes(@AuthenticationPrincipal AuthUser me, @PathVariable UUID teamId, @Valid @RequestBody EnvelopesRequest req) {
        return service.setEnvelopes(me, teamId, req);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une demande", description = "Accès : membre ou manager de l'équipe. Exercice de l'année en cours, ouvert.")
    @ApiResponse(responseCode = "201", description = "Demande en attente")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas membre de l'équipe (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Équipe introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Exercice clos (FISCAL_YEAR_CLOSED)")
    public PurchaseDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PurchaseRequestBody req) {
        return service.create(me, req);
    }

    @GetMapping("/requests")
    @Operation(summary = "Demandes", description = "Accès : équipes visibles. Filtres par équipe et statut.")
    @ApiResponse(responseCode = "200", description = "Demandes")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Équipe introuvable (NOT_FOUND)")
    public List<PurchaseDto> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) UUID teamId,
                                  @RequestParam(required = false) PurchaseStatus status) {
        return service.list(me, teamId, status);
    }

    @PostMapping("/requests/{id}/approve")
    @Operation(summary = "Valider", description = "Accès : manager ou ADMIN. Le montant est engagé ; au-delà du restant de la catégorie, seul un ADMIN déroge (`override: true` et motif).")
    @ApiResponse(responseCode = "200", description = "Demande validée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MGR)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "BUDGET_EXCEEDED, FISCAL_YEAR_CLOSED ou INVALID_STATUS_TRANSITION")
    public PurchaseDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody(required = false) ApproveRequest req) {
        return service.approve(me, id, req);
    }

    @PostMapping("/requests/{id}/reject")
    @Operation(summary = "Refuser", description = "Accès : manager ou ADMIN. Motif obligatoire.")
    @ApiResponse(responseCode = "200", description = "Demande refusée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MGR)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "Déjà traitée (INVALID_STATUS_TRANSITION)")
    public PurchaseDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        return service.reject(me, id, req.reason());
    }

    @PostMapping("/requests/{id}/spend")
    @Operation(summary = "Enregistrer la dépense", description = "Accès : manager ou ADMIN. Demande validée ; le montant réel remplace l'engagement.")
    @ApiResponse(responseCode = "200", description = "Demande dépensée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MGR)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "FISCAL_YEAR_CLOSED ou INVALID_STATUS_TRANSITION")
    public PurchaseDto spend(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody SpendRequest req) {
        return service.spend(me, id, req);
    }

    @DeleteMapping("/requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Annuler ma demande", description = "Accès : auteur, demande en attente.")
    @ApiResponse(responseCode = "204", description = "Annulée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas l'auteur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "Plus en attente (INVALID_STATUS_TRANSITION)")
    public void cancel(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.cancel(me, id);
    }

    @PostMapping("/years/{year}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clore l'exercice", description = "Accès : ADMIN. Plus aucune demande ni dépense sur cet exercice.")
    @ApiResponse(responseCode = "204", description = "Exercice clos")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Exercice introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà clos (FISCAL_YEAR_CLOSED)")
    public void close(@AuthenticationPrincipal AuthUser me, @PathVariable int year) {
        service.closeYear(me, year);
    }

    @GetMapping("/alerts")
    @Operation(summary = "Alertes", description = "Accès : manager (ses équipes) ou ADMIN (toutes). Catégories consommées à 80 % et plus sur l'année en cours.")
    @ApiResponse(responseCode = "200", description = "Alertes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<AlertDto> alerts(@AuthenticationPrincipal AuthUser me) {
        return service.alerts(me);
    }

    @GetMapping("/people")
    @Operation(summary = "Collègues", description = "Accès : authentifié. Utilisateurs actifs, pour composer les équipes.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
