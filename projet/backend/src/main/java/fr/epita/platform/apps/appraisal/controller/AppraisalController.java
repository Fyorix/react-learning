package fr.epita.platform.apps.appraisal.controller;

import fr.epita.platform.apps.appraisal.dto.AppraisalDtos.*;
import fr.epita.platform.apps.appraisal.service.AppraisalService;
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

@Tag(name = "Entretiens annuels", description = "Campagnes d'entretiens : auto-évaluation, évaluation du manager, objectifs et double signature.")
@RestController
@RequestMapping("/api/appraisals")
@RequiredArgsConstructor
public class AppraisalController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String A404 = "Entretien introuvable ou non accessible (NOT_FOUND)";
    private static final String STEP409 = "INVALID_STEP (mauvaise étape) ou DEADLINE_PASSED (date limite dépassée)";

    private final AppraisalService service;

    @PostMapping("/campaigns")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lancer une campagne", description = "Accès : ADMIN. Un entretien est créé pour chaque salarié ayant un manager (relations de `/admin/reports`). Une campagne par année.")
    @ApiResponse(responseCode = "201", description = "Campagne lancée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Campagne déjà lancée pour cette année (CAMPAIGN_EXISTS)")
    public CampaignDto launch(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CampaignRequest req) {
        return service.launch(me, req);
    }

    @GetMapping("/campaigns")
    @Operation(summary = "Campagnes", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "200", description = "Campagnes")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public List<CampaignDto> campaigns(@AuthenticationPrincipal AuthUser me) {
        return service.campaigns(me);
    }

    @GetMapping("/campaigns/{id}/progress")
    @Operation(summary = "Avancement d'une campagne", description = "Accès : ADMIN. Nombre d'entretiens par étape et en retard après la date limite.")
    @ApiResponse(responseCode = "200", description = "Avancement")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Campagne introuvable (NOT_FOUND)")
    public ProgressDto progress(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.progress(me, id);
    }

    @GetMapping("/campaigns/{id}/appraisals")
    @Operation(summary = "Entretiens d'une campagne", description = "Accès : ADMIN. Liste des entretiens avec leur étape et leur retard éventuel (pour les réouvertures).")
    @ApiResponse(responseCode = "200", description = "Entretiens")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Campagne introuvable (NOT_FOUND)")
    public List<AppraisalSummary> campaignAppraisals(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.campaignAppraisals(me, id);
    }

    @GetMapping("/me")
    @Operation(summary = "Mon entretien de l'année", description = "Accès : authentifié. Entretien de la campagne la plus récente.")
    @ApiResponse(responseCode = "200", description = "Entretien")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Aucun entretien (NOT_FOUND)")
    public AppraisalDto mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @GetMapping("/team")
    @Operation(summary = "Entretiens de mes salariés", description = "Accès : manager. Toutes campagnes, la plus récente d'abord.")
    @ApiResponse(responseCode = "200", description = "Entretiens")
    @ApiResponse(responseCode = "401", description = U401)
    public List<AppraisalSummary> team(@AuthenticationPrincipal AuthUser me) {
        return service.team(me);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un entretien", description = "Accès : salarié, manager ou ADMIN. Contenu selon l'étape et le rôle (auto-évaluation masquée au manager avant soumission).")
    @ApiResponse(responseCode = "200", description = "Entretien")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = A404)
    public AppraisalDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @GetMapping("/{id}/self-review")
    @Operation(summary = "Réponses de l'auto-évaluation", description = "Accès : salarié, ou manager / ADMIN une fois l'auto-évaluation soumise (404 avant).")
    @ApiResponse(responseCode = "200", description = "Réponses")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Entretien introuvable ou auto-évaluation pas encore soumise (NOT_FOUND)")
    public List<AnswerDto> selfReview(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.selfAnswers(me, id);
    }

    @PutMapping("/{id}/self-review")
    @Operation(summary = "Enregistrer mon auto-évaluation", description = "Accès : salarié, étape SELF_REVIEW. Brouillon : les réponses partielles sont acceptées.")
    @ApiResponse(responseCode = "200", description = "Entretien")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé au salarié (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = A404)
    @ApiResponse(responseCode = "409", description = STEP409)
    public AppraisalDto saveSelf(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody SelfReviewRequest req) {
        return service.saveSelfReview(me, id, req);
    }

    @PostMapping("/{id}/self-review/submit")
    @Operation(summary = "Soumettre mon auto-évaluation", description = "Accès : salarié. Toutes les questions doivent avoir une réponse ; l'entretien passe au manager.")
    @ApiResponse(responseCode = "200", description = "Entretien en MANAGER_REVIEW")
    @ApiResponse(responseCode = "400", description = "Réponses manquantes (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé au salarié (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = A404)
    @ApiResponse(responseCode = "409", description = STEP409)
    public AppraisalDto submitSelf(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.submitSelfReview(me, id);
    }

    @PutMapping("/{id}/manager-review")
    @Operation(summary = "Enregistrer l'évaluation du manager", description = "Accès : manager, étape MANAGER_REVIEW. Réponses et objectifs (5 maximum) ; remplace le brouillon.")
    @ApiResponse(responseCode = "200", description = "Entretien")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé au manager (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = A404)
    @ApiResponse(responseCode = "409", description = STEP409)
    public AppraisalDto saveManager(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ManagerReviewRequest req) {
        return service.saveManagerReview(me, id, req);
    }

    @PostMapping("/{id}/manager-review/submit")
    @Operation(summary = "Soumettre l'évaluation du manager", description = "Accès : manager. Toutes les questions remplies et 1 à 5 objectifs ; l'entretien passe à la signature du salarié.")
    @ApiResponse(responseCode = "200", description = "Entretien en EMPLOYEE_SIGNATURE")
    @ApiResponse(responseCode = "400", description = "Réponses ou objectifs manquants (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé au manager (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = A404)
    @ApiResponse(responseCode = "409", description = STEP409)
    public AppraisalDto submitManager(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.submitManagerReview(me, id);
    }

    @PostMapping("/{id}/sign")
    @Operation(summary = "Signer", description = "Accès : le salarié (EMPLOYEE_SIGNATURE, commentaire final facultatif) puis le manager (MANAGER_SIGNATURE). Après les deux signatures, l'entretien est figé.")
    @ApiResponse(responseCode = "200", description = "Entretien")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Ni le salarié ni le manager (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = A404)
    @ApiResponse(responseCode = "409", description = STEP409)
    public AppraisalDto sign(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody(required = false) SignRequest req) {
        return service.sign(me, id, req == null ? null : req.finalComment());
    }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Rouvrir la saisie", description = "Accès : ADMIN. Accorde quelques jours de saisie après la date limite (7 par défaut) ; impossible sur un entretien signé.")
    @ApiResponse(responseCode = "200", description = "Entretien")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Entretien introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Entretien déjà signé (INVALID_STEP)")
    public AppraisalDto reopen(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody(required = false) ReopenRequest req) {
        return service.reopen(me, id, req == null ? null : req.days());
    }
}
