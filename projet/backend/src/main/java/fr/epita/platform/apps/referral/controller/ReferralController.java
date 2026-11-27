package fr.epita.platform.apps.referral.controller;

import fr.epita.platform.apps.referral.dto.ReferralDtos.*;
import fr.epita.platform.apps.referral.entity.ReferralStatus;
import fr.epita.platform.apps.referral.service.ReferralService;
import fr.epita.platform.common.AuthUser;
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

@Tag(name = "Cooptation", description = "Recommandations de candidats par les salariés, suivi RH et primes de cooptation.")
@RestController
@RequestMapping("/api/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String R404 = "Recommandation introuvable (NOT_FOUND)";

    private final ReferralService service;

    @GetMapping("/jobs")
    @Operation(summary = "Postes à pourvoir", description = "Accès : authentifié. Postes ouverts et montant de la prime (tous les postes et le nombre de recommandations pour un ADMIN).")
    @ApiResponse(responseCode = "200", description = "Postes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<JobDto> jobs(@AuthenticationPrincipal AuthUser me) {
        return service.jobs(me);
    }

    @PostMapping("/jobs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Publier un poste", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "201", description = "Poste publié")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public JobDto createJob(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody JobRequest req) {
        return service.createJob(me, req);
    }

    @PutMapping("/jobs/{id}")
    @Operation(summary = "Modifier ou fermer un poste", description = "Accès : ADMIN. `open: false` ferme le poste aux nouvelles recommandations.")
    @ApiResponse(responseCode = "200", description = "Poste")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Poste introuvable (NOT_FOUND)")
    public JobDto updateJob(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody JobRequest req) {
        return service.updateJob(me, id, req);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Recommander", description = "Accès : authentifié. Pas soi-même ; un même e-mail une seule fois par poste (le premier coopteur est retenu).")
    @ApiResponse(responseCode = "201", description = "Recommandation envoyée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Poste introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "SELF_REFERRAL, ALREADY_REFERRED ou JOB_CLOSED")
    public MyReferralDto refer(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ReferralRequest req) {
        return service.refer(me, req);
    }

    @GetMapping("/me")
    @Operation(summary = "Mes recommandations", description = "Accès : authentifié. Avancement, sans les commentaires internes RH.")
    @ApiResponse(responseCode = "200", description = "Recommandations")
    @ApiResponse(responseCode = "401", description = U401)
    public List<MyReferralDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @GetMapping("/admin")
    @Operation(summary = "Toutes les recommandations", description = "Accès : ADMIN. Filtres facultatifs par statut et par poste.")
    @ApiResponse(responseCode = "200", description = "Recommandations")
    @ApiResponse(responseCode = "400", description = "Paramètre invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public List<ReferralDto> admin(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) ReferralStatus status,
                                   @RequestParam(required = false) UUID jobId) {
        return service.adminList(me, status, jobId);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut", description = "Accès : ADMIN. Étape suivante uniquement (SUBMITTED → IN_REVIEW → HIRED → TRIAL_VALIDATED) ou REJECTED avant la validation de l'essai ; commentaire interne facultatif.")
    @ApiResponse(responseCode = "200", description = "Recommandation")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "Transition interdite (INVALID_STATUS_TRANSITION)")
    public ReferralDto status(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        return service.changeStatus(me, id, req);
    }

    @GetMapping("/admin/bonuses")
    @Operation(summary = "Primes", description = "Accès : ADMIN. `status=due` (par défaut) : essai validé et coopteur actif ; `status=paid` : primes versées.")
    @ApiResponse(responseCode = "200", description = "Recommandations concernées")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public List<ReferralDto> bonuses(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String status) {
        return service.bonuses(me, status);
    }

    @PostMapping("/{id}/bonus-paid")
    @Operation(summary = "Marquer la prime versée", description = "Accès : ADMIN. Seulement après TRIAL_VALIDATED et si le coopteur est encore actif.")
    @ApiResponse(responseCode = "200", description = "Prime versée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "TRIAL_NOT_VALIDATED, REFERRER_INACTIVE ou INVALID_STATUS_TRANSITION")
    public ReferralDto bonusPaid(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.bonusPaid(me, id);
    }
}
