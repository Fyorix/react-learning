package fr.epita.platform.apps.farewell.controller;

import fr.epita.platform.apps.farewell.dto.FarewellDtos.*;
import fr.epita.platform.apps.farewell.service.FarewellService;
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

@Tag(name = "Cagnotte", description = "Cagnottes de départ (anniversaire, naissance…) créées en 4 étapes, participation et carte collective.")
@RestController
@RequestMapping("/api/farewell")
@RequiredArgsConstructor
public class FarewellController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String N404 = "Cagnotte introuvable ou non visible — notamment pour le bénéficiaire avant la clôture (NOT_FOUND)";
    private static final String DRAFT409 = "Cagnotte déjà publiée (INVALID_STATUS_TRANSITION)";

    private final FarewellService service;
    private final UserRepository users;

    @PostMapping("/pots")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Étape 1 : créer le brouillon", description = "Accès : authentifié (devient l'organisateur). Le bénéficiaire ne peut pas être l'organisateur.")
    @ApiResponse(responseCode = "201", description = "Brouillon créé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Bénéficiaire introuvable (NOT_FOUND)")
    public PotDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody IdentityStep req) {
        return service.create(me, req);
    }

    @PutMapping("/pots/{id}/steps/identity")
    @Operation(summary = "Étape 1 : modifier", description = "Accès : organisateur, brouillon. Retour en arrière dans le wizard.")
    @ApiResponse(responseCode = "200", description = "Brouillon")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = N404)
    @ApiResponse(responseCode = "409", description = DRAFT409)
    public PotDto identity(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody IdentityStep req) {
        return service.identity(me, id, req);
    }

    @PutMapping("/pots/{id}/steps/goal")
    @Operation(summary = "Étape 2 : objectif et clôture", description = "Accès : organisateur, brouillon. Objectif facultatif ; clôture dans le futur, à 60 jours maximum.")
    @ApiResponse(responseCode = "200", description = "Brouillon")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = N404)
    @ApiResponse(responseCode = "409", description = DRAFT409)
    public PotDto goal(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody GoalStep req) {
        return service.goal(me, id, req);
    }

    @PutMapping("/pots/{id}/steps/invitees")
    @Operation(summary = "Étape 3 : invités", description = "Accès : organisateur, brouillon. Ni le bénéficiaire ni l'organisateur.")
    @ApiResponse(responseCode = "200", description = "Brouillon")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Cagnotte ou invité introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = DRAFT409)
    public PotDto invitees(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody InviteesStep req) {
        return service.invitees(me, id, req);
    }

    @GetMapping("/pots/{id}/review")
    @Operation(summary = "Étape 4 : récapitulatif", description = "Accès : organisateur, brouillon. Validité de chaque étape et possibilité de publier.")
    @ApiResponse(responseCode = "200", description = "Récapitulatif")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = N404)
    @ApiResponse(responseCode = "409", description = DRAFT409)
    public ReviewDto review(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.review(me, id);
    }

    @PostMapping("/pots/{id}/open")
    @Operation(summary = "Publier", description = "Accès : organisateur. Toutes les étapes doivent être valides.")
    @ApiResponse(responseCode = "200", description = "Cagnotte ouverte")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = N404)
    @ApiResponse(responseCode = "409", description = "POT_INCOMPLETE ou INVALID_STATUS_TRANSITION")
    public PotDto open(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.open(me, id);
    }

    @GetMapping("/pots")
    @Operation(summary = "Mes cagnottes", description = "Accès : authentifié. Organisées (y compris brouillons), où je suis invité, reçues après clôture.")
    @ApiResponse(responseCode = "200", description = "Cagnottes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<PotDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @GetMapping("/pots/{id}")
    @Operation(summary = "Détail d'une cagnotte", description = "Accès : organisateur, invité, bénéficiaire après clôture, ADMIN. Total et progression pour tous ; montants individuels pour l'organisateur.")
    @ApiResponse(responseCode = "200", description = "Cagnotte")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = N404)
    public PotDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PutMapping("/pots/{id}/contributions/me")
    @Operation(summary = "Participer ou modifier", description = "Accès : organisateur ou invité. Une participation par personne, modifiable jusqu'à la clôture.")
    @ApiResponse(responseCode = "200", description = "Cagnotte")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = N404)
    @ApiResponse(responseCode = "409", description = "Cagnotte close ou pas encore publiée (POT_CLOSED)")
    public PotDto contribute(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ContributionRequest req) {
        return service.contribute(me, id, req);
    }

    @DeleteMapping("/pots/{id}/contributions/me")
    @Operation(summary = "Retirer ma participation", description = "Accès : participant, avant la clôture.")
    @ApiResponse(responseCode = "200", description = "Cagnotte")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Cagnotte ou participation introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Cagnotte close (POT_CLOSED)")
    public PotDto withdraw(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.withdraw(me, id);
    }

    @PostMapping("/pots/{id}/close")
    @Operation(summary = "Clôturer", description = "Accès : organisateur. La carte devient visible du bénéficiaire. Clôture automatique à `closesAt` sinon.")
    @ApiResponse(responseCode = "200", description = "Cagnotte close")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé à l'organisateur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = N404)
    @ApiResponse(responseCode = "409", description = "Déjà close ou brouillon (POT_CLOSED)")
    public PotDto close(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.close(me, id);
    }

    @GetMapping("/pots/{id}/card")
    @Operation(summary = "Carte collective", description = "Accès : organisateur, invités, bénéficiaire après clôture, ADMIN. Messages sans montants.")
    @ApiResponse(responseCode = "200", description = "Carte")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = N404)
    public CardDto card(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.card(me, id);
    }

    @DeleteMapping("/pots/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer une cagnotte", description = "Accès : organisateur (brouillon ou sans participation) ou ADMIN.")
    @ApiResponse(responseCode = "204", description = "Supprimée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Ni organisateur ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = N404)
    @ApiResponse(responseCode = "409", description = "Des participations existent (POT_HAS_CONTRIBUTIONS)")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @GetMapping("/people")
    @Operation(summary = "Collègues", description = "Accès : authentifié. Utilisateurs actifs, pour choisir bénéficiaire et invités.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
