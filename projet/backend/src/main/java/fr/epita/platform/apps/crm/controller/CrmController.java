package fr.epita.platform.apps.crm.controller;

import fr.epita.platform.apps.crm.dto.CrmDtos.*;
import fr.epita.platform.apps.crm.entity.PipelineStage;
import fr.epita.platform.apps.crm.service.CrmService;
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

@Tag(name = "CRM", description = "Comptes, contacts, opportunités, pipeline par étape, interactions, relances et prévisions.")
@RestController
@RequestMapping("/api/crm")
@RequiredArgsConstructor
public class CrmController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String SAL = "Réservé aux commerciaux, rôle SALES (FORBIDDEN)";
    private static final String A404 = "Compte introuvable (NOT_FOUND)";
    private static final String O404 = "Opportunité introuvable (NOT_FOUND)";

    private final CrmService service;
    private final UserRepository users;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. SALES le cas échéant (un ADMIN l'a d'office).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @GetMapping("/accounts")
    @Operation(summary = "Comptes", description = "Accès : SALES. Recherche nom / secteur, filtre par commercial.")
    @ApiResponse(responseCode = "200", description = "Comptes")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SAL)
    public List<AccountSummary> accounts(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String search, @RequestParam(required = false) UUID ownerId) {
        return service.accounts(me, search, ownerId);
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un compte", description = "Accès : SALES (responsable : moi, ou un autre commercial si ADMIN).")
    @ApiResponse(responseCode = "201", description = "Compte")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SAL)
    public AccountDto createAccount(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AccountRequest req) {
        return service.createAccount(me, req);
    }

    @GetMapping("/accounts/{id}")
    @Operation(summary = "Fiche compte", description = "Accès : SALES. Contacts, opportunités, interactions.")
    @ApiResponse(responseCode = "200", description = "Compte")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SAL)
    @ApiResponse(responseCode = "404", description = A404)
    public AccountDto account(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.account(me, id);
    }

    @PutMapping("/accounts/{id}")
    @Operation(summary = "Modifier un compte", description = "Accès : commercial responsable, ADMIN.")
    @ApiResponse(responseCode = "200", description = "Compte")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le responsable du compte (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = A404)
    public AccountDto updateAccount(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody AccountRequest req) {
        return service.updateAccount(me, id, req);
    }

    @PostMapping("/accounts/{id}/contacts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un contact", description = "Accès : SALES. E-mail unique dans le compte.")
    @ApiResponse(responseCode = "201", description = "Contact")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SAL)
    @ApiResponse(responseCode = "404", description = A404)
    @ApiResponse(responseCode = "409", description = "E-mail déjà présent dans le compte (CONTACT_EXISTS)")
    public ContactDto addContact(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ContactRequest req) {
        return service.addContact(me, id, req);
    }

    @PostMapping("/opportunities")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une opportunité", description = "Accès : SALES. Créée en QUALIFICATION, appartient à son créateur.")
    @ApiResponse(responseCode = "201", description = "Opportunité")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SAL)
    @ApiResponse(responseCode = "404", description = A404)
    public OpportunityDto createOpportunity(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody OpportunityRequest req) {
        return service.createOpportunity(me, req);
    }

    @GetMapping("/opportunities")
    @Operation(summary = "Pipeline", description = "Accès : SALES. Opportunités groupées par étape, montants bruts et pondérés.")
    @ApiResponse(responseCode = "200", description = "Colonnes du pipeline")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SAL)
    public List<StageColumn> pipeline(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) PipelineStage stage, @RequestParam(required = false) UUID ownerId) {
        return service.pipeline(me, stage, ownerId);
    }

    @GetMapping("/opportunities/{id}")
    @Operation(summary = "Détail d'une opportunité", description = "Accès : SALES. Avec ses interactions et les étapes accessibles.")
    @ApiResponse(responseCode = "200", description = "Opportunité")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SAL)
    @ApiResponse(responseCode = "404", description = O404)
    public OpportunityDetailDto opportunity(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.opportunity(me, id);
    }

    @PutMapping("/opportunities/{id}")
    @Operation(summary = "Modifier une opportunité", description = "Accès : son commercial, ADMIN. Plus modifiable une fois close.")
    @ApiResponse(responseCode = "200", description = "Opportunité")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le commercial de l'opportunité (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = O404)
    @ApiResponse(responseCode = "409", description = "Opportunité close (OPPORTUNITY_CLOSED)")
    public OpportunityDto updateOpportunity(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody OpportunityRequest req) {
        return service.updateOpportunity(me, id, req);
    }

    @PostMapping("/opportunities/{id}/stage")
    @Operation(summary = "Changer d'étape", description = "Accès : son commercial, ADMIN. Étape voisine, ou WON / LOST depuis n'importe quelle étape ; LOST exige un motif.")
    @ApiResponse(responseCode = "200", description = "Opportunité")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le commercial de l'opportunité (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = O404)
    @ApiResponse(responseCode = "409", description = "OPPORTUNITY_CLOSED ou INVALID_STAGE_TRANSITION")
    public OpportunityDto changeStage(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody StageRequest req) {
        return service.changeStage(me, id, req);
    }

    @PostMapping("/interactions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistrer une interaction", description = "Accès : SALES. Sur un compte ou une opportunité ; date pas dans le futur.")
    @ApiResponse(responseCode = "201", description = "Interaction")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SAL)
    @ApiResponse(responseCode = "404", description = "Compte ou opportunité introuvable (NOT_FOUND)")
    public InteractionDto addInteraction(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody InteractionRequest req) {
        return service.addInteraction(me, req);
    }

    @GetMapping("/tasks/me")
    @Operation(summary = "Mes relances", description = "Accès : SALES. Filtre `due` : today, overdue, week (non faites) ; sans filtre : toutes.")
    @ApiResponse(responseCode = "200", description = "Tâches")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SAL)
    public List<TaskDto> myTasks(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String due) {
        return service.myTasks(me, due);
    }

    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une relance", description = "Accès : SALES. Liée facultativement à un compte ou une opportunité.")
    @ApiResponse(responseCode = "201", description = "Tâche")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SAL)
    @ApiResponse(responseCode = "404", description = "Compte ou opportunité introuvable (NOT_FOUND)")
    public TaskDto createTask(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TaskRequest req) {
        return service.createTask(me, req);
    }

    @PatchMapping("/tasks/{id}")
    @Operation(summary = "Marquer une relance", description = "Accès : propriétaire de la tâche.")
    @ApiResponse(responseCode = "200", description = "Tâche")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Tâche introuvable (NOT_FOUND)")
    public TaskDto patchTask(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody TaskPatch req) {
        return service.patchTask(me, id, req.done());
    }

    @GetMapping("/forecast")
    @Operation(summary = "Prévisions", description = "Accès : SALES. Par mois : somme des montants × probabilité des opportunités ouvertes ; 1 à 24 mois.")
    @ApiResponse(responseCode = "200", description = "Prévisions")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SAL)
    public ForecastDto forecast(@AuthenticationPrincipal AuthUser me, @RequestParam(defaultValue = "6") int months, @RequestParam(required = false) UUID ownerId) {
        return service.forecast(me, months, ownerId);
    }

    @GetMapping("/people")
    @Operation(summary = "Commerciaux", description = "Accès : SALES. Utilisateurs actifs ayant le rôle SALES (filtres, attribution d'un compte).")
    @ApiResponse(responseCode = "200", description = "Commerciaux")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SAL)
    public List<UserSummary> people(@AuthenticationPrincipal AuthUser me) {
        var ids = new java.util.HashSet<>(service.salesPeople(me));
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().filter(u -> ids.contains(u.getId())).map(UserSummary::of).toList();
    }
}
