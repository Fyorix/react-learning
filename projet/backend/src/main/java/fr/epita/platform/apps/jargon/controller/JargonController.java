package fr.epita.platform.apps.jargon.controller;

import fr.epita.platform.apps.jargon.dto.JargonDtos.*;
import fr.epita.platform.apps.jargon.service.JargonService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
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

@Tag(name = "Jargon", description = "Glossaire collectif des acronymes : autocomplétion, définitions votées, modération.")
@RestController
@RequestMapping("/api/jargon")
@RequiredArgsConstructor
public class JargonController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String MOD = "Réservé aux MODERATOR (FORBIDDEN)";
    private static final String D404 = "Définition introuvable ou non publiée (NOT_FOUND)";

    private final JargonService service;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. MODERATOR le cas échéant (un ADMIN l'a d'office).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @GetMapping("/suggest")
    @Operation(summary = "Autocomplétion", description = "Accès : authentifié. Préfixe d'au moins 1 caractère, casse et accents ignorés, 10 suggestions maximum, termes publiés ; les recherches sans résultat sont comptées.")
    @ApiResponse(responseCode = "200", description = "Suggestions")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public List<Suggestion> suggest(@RequestParam(required = false) String q, @RequestParam(defaultValue = "10") int limit) {
        return service.suggest(q, limit);
    }

    @GetMapping("/terms")
    @Operation(summary = "Glossaire", description = "Accès : authentifié. Termes publiés, paginés ; filtres texte (terme ou forme développée), première lettre, équipe d'origine.")
    @ApiResponse(responseCode = "200", description = "Page de termes")
    @ApiResponse(responseCode = "401", description = U401)
    public PageResponse<TermSummary> list(@RequestParam(required = false) String search, @RequestParam(required = false) String letter,
                                          @RequestParam(required = false) String team, @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.list(search, letter, team, page, size);
    }

    @GetMapping("/terms/{id}")
    @Operation(summary = "Fiche d'un terme", description = "Accès : authentifié. Définitions publiées triées par votes « utile », termes liés.")
    @ApiResponse(responseCode = "200", description = "Terme")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Terme introuvable ou non publié (NOT_FOUND)")
    public TermDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/terms")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Proposer un terme", description = "Accès : authentifié. Terme et première définition restent en attente de modération.")
    @ApiResponse(responseCode = "201", description = "Contribution")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "409", description = "Terme existant, casse et accents ignorés (TERM_EXISTS)")
    public ContributionDto proposeTerm(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TermRequest req) {
        return service.proposeTerm(me, req);
    }

    @PostMapping("/terms/{id}/definitions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Proposer une définition", description = "Accès : authentifié. Sur un terme publié ; en attente de modération.")
    @ApiResponse(responseCode = "201", description = "Contribution")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Terme introuvable ou non publié (NOT_FOUND)")
    public ContributionDto proposeDefinition(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody DefinitionRequest req) {
        return service.proposeDefinition(me, id, req);
    }

    @PutMapping("/definitions/{id}/vote")
    @Operation(summary = "Voter « utile »", description = "Accès : authentifié. Un vote par définition (idempotent), jamais sur sa propre définition. Renvoie le nombre de votes.")
    @ApiResponse(responseCode = "200", description = "Nombre de votes")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = D404)
    @ApiResponse(responseCode = "409", description = "Propre définition (OWN_DEFINITION)")
    public long vote(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.vote(me, id);
    }

    @DeleteMapping("/definitions/{id}/vote")
    @Operation(summary = "Retirer mon vote", description = "Accès : authentifié. Idempotent. Renvoie le nombre de votes.")
    @ApiResponse(responseCode = "200", description = "Nombre de votes")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = D404)
    public long unvote(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.unvote(me, id);
    }

    @GetMapping("/moderation")
    @Operation(summary = "Contributions en attente", description = "Accès : MODERATOR. Nouveaux termes (avec leur définition) et nouvelles définitions.")
    @ApiResponse(responseCode = "200", description = "Contributions")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MOD)
    public List<ContributionDto> moderation(@AuthenticationPrincipal AuthUser me) {
        return service.moderation(me);
    }

    @PostMapping("/moderation/{id}/approve")
    @Operation(summary = "Publier une contribution", description = "Accès : MODERATOR, jamais sur sa propre contribution. `id` : terme ou définition.")
    @ApiResponse(responseCode = "200", description = "Contribution publiée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MOD)
    @ApiResponse(responseCode = "404", description = "Contribution introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "SELF_MODERATION ou INVALID_STATUS_TRANSITION")
    public ContributionDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.approve(me, id);
    }

    @PostMapping("/moderation/{id}/reject")
    @Operation(summary = "Refuser une contribution", description = "Accès : MODERATOR, jamais sur sa propre contribution. Motif obligatoire.")
    @ApiResponse(responseCode = "200", description = "Contribution refusée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MOD)
    @ApiResponse(responseCode = "404", description = "Contribution introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "SELF_MODERATION ou INVALID_STATUS_TRANSITION")
    public ContributionDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        return service.reject(me, id, req.reason());
    }

    @GetMapping("/me/contributions")
    @Operation(summary = "Mes contributions", description = "Accès : authentifié. Termes et définitions proposés, avec leur statut et le motif d'un refus.")
    @ApiResponse(responseCode = "200", description = "Contributions")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ContributionDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @GetMapping("/admin/missing")
    @Operation(summary = "Recherches sans résultat", description = "Accès : ADMIN. Les 50 plus fréquentes (comptage anonyme).")
    @ApiResponse(responseCode = "200", description = "Recherches")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    public List<MissingDto> missing(@AuthenticationPrincipal AuthUser me) {
        return service.missing(me);
    }
}
