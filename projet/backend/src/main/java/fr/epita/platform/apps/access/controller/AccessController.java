package fr.epita.platform.apps.access.controller;

import fr.epita.platform.apps.access.dto.AccessDtos.*;
import fr.epita.platform.apps.access.service.AccessService;
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

@Tag(name = "Demandes d'accès", description = "Droits sur les outils internes : demande, double validation, durée, revue et audit.")
@RestController
@RequestMapping("/api/access")
@RequiredArgsConstructor
public class AccessController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String R404 = "Demande introuvable (NOT_FOUND)";
    private static final String G404 = "Accès introuvable (NOT_FOUND)";
    private static final String OWNER = "Réservé au propriétaire de l'outil ou à un ADMIN (FORBIDDEN)";

    private final AccessService service;
    private final UserRepository users;

    @GetMapping("/tools")
    @Operation(summary = "Outils et niveaux", description = "Accès : authentifié. Avec mon niveau actuel éventuel.")
    @ApiResponse(responseCode = "200", description = "Outils")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ToolDto> tools(@AuthenticationPrincipal AuthUser me) {
        return service.tools(me);
    }

    @PostMapping("/tools")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un outil", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "201", description = "Outil")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Propriétaire introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Nom existant (TOOL_EXISTS)")
    public ToolDto addTool(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ToolRequest req) {
        return service.addTool(me, req);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Demander un accès", description = "Accès : authentifié. 1 à 365 jours (90 maximum pour ADMIN) ; validée par le manager puis par le propriétaire.")
    @ApiResponse(responseCode = "201", description = "Demande")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Outil introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "ALREADY_GRANTED (niveau égal ou supérieur déjà actif) ou REQUEST_PENDING")
    public AccessRequestDto request(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AccessRequestBody req) {
        return service.request(me, req);
    }

    @GetMapping("/requests/me")
    @Operation(summary = "Mes demandes", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Demandes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<AccessRequestDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @GetMapping("/requests/to-approve")
    @Operation(summary = "À valider", description = "Accès : manager (étape manager) ou propriétaire (étape propriétaire) ; ADMIN : toutes.")
    @ApiResponse(responseCode = "200", description = "Demandes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<AccessRequestDto> toApprove(@AuthenticationPrincipal AuthUser me) {
        return service.toApprove(me);
    }

    @PostMapping("/requests/{id}/approve")
    @Operation(summary = "Valider l'étape courante", description = "Accès : validateur de l'étape. Jamais sa propre demande ; si le manager est le propriétaire, une seule validation octroie l'accès.")
    @ApiResponse(responseCode = "200", description = "Demande")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le validateur de l'étape (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "SELF_APPROVAL, ALREADY_GRANTED ou INVALID_STATUS_TRANSITION")
    public AccessRequestDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.approve(me, id);
    }

    @PostMapping("/requests/{id}/reject")
    @Operation(summary = "Refuser", description = "Accès : validateur de l'étape. Motif obligatoire.")
    @ApiResponse(responseCode = "200", description = "Demande refusée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le validateur de l'étape (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "SELF_APPROVAL ou INVALID_STATUS_TRANSITION")
    public AccessRequestDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        return service.reject(me, id, req.reason());
    }

    @GetMapping("/grants/me")
    @Operation(summary = "Mes accès actifs", description = "Accès : authentifié. Avec leur échéance.")
    @ApiResponse(responseCode = "200", description = "Accès")
    @ApiResponse(responseCode = "401", description = U401)
    public List<GrantDto> myGrants(@AuthenticationPrincipal AuthUser me) {
        return service.myGrants(me);
    }

    @GetMapping("/tools/{id}/grants")
    @Operation(summary = "Accès à un outil", description = "Accès : propriétaire ou ADMIN. Accès actifs ; `reviewDue` si non confirmés depuis 180 jours.")
    @ApiResponse(responseCode = "200", description = "Accès")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWNER)
    @ApiResponse(responseCode = "404", description = "Outil introuvable (NOT_FOUND)")
    public List<GrantDto> toolGrants(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.toolGrants(me, id);
    }

    @PostMapping("/grants/{id}/confirm")
    @Operation(summary = "Confirmer un accès (revue)", description = "Accès : propriétaire ou ADMIN.")
    @ApiResponse(responseCode = "200", description = "Accès")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWNER)
    @ApiResponse(responseCode = "404", description = G404)
    @ApiResponse(responseCode = "409", description = "Accès plus actif (INVALID_STATUS_TRANSITION)")
    public GrantDto confirm(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.confirm(me, id);
    }

    @DeleteMapping("/grants/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Révoquer un accès", description = "Accès : propriétaire ou ADMIN. Journalisé.")
    @ApiResponse(responseCode = "204", description = "Révoqué")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWNER)
    @ApiResponse(responseCode = "404", description = G404)
    @ApiResponse(responseCode = "409", description = "Accès plus actif (INVALID_STATUS_TRANSITION)")
    public void revoke(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.revoke(me, id);
    }

    @GetMapping("/admin/audit")
    @Operation(summary = "Journal d'audit", description = "Accès : ADMIN. Octrois, remplacements, confirmations, révocations et expirations ; filtres par utilisateur et outil.")
    @ApiResponse(responseCode = "200", description = "Journal")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public List<AuditDto> audit(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) UUID userId, @RequestParam(required = false) UUID toolId) {
        return service.audit(me, userId, toolId);
    }

    @GetMapping("/people")
    @Operation(summary = "Utilisateurs", description = "Accès : authentifié. Utilisateurs actifs (propriétaires possibles).")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
