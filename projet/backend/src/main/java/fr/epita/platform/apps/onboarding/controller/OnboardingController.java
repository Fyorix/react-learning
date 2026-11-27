package fr.epita.platform.apps.onboarding.controller;

import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.JourneyDto;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.JourneyRequest;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.TemplateDto;
import fr.epita.platform.apps.onboarding.dto.OnboardingDtos.TemplateRequest;
import fr.epita.platform.apps.onboarding.service.OnboardingService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Onboarding", description = "Parcours d'intégration des nouveaux arrivants, avec manager et parrain.")
@RestController
@RequestMapping("/api/onboarding")
@RequiredArgsConstructor
public class OnboardingController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";

    private final OnboardingService service;
    private final UserRepository users;

    @GetMapping("/templates")
    @Operation(summary = "Modèles de parcours", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "200", description = "Modèles")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public List<TemplateDto> templates(@AuthenticationPrincipal AuthUser me) {
        return service.templates(me);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un modèle", description = "Accès : ADMIN. Nom unique ; 1 à 50 tâches avec responsable et échéance relative à J0.")
    @ApiResponse(responseCode = "201", description = "Modèle créé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Nom déjà utilisé (TEMPLATE_NAME_EXISTS)")
    public TemplateDto createTemplate(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TemplateRequest req) {
        return service.createTemplate(me, req);
    }

    @PutMapping("/templates/{id}")
    @Operation(summary = "Modifier un modèle", description = "Accès : ADMIN. Les parcours déjà lancés ne changent pas (tâches copiées au lancement).")
    @ApiResponse(responseCode = "200", description = "Modèle modifié")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Modèle introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Nom déjà utilisé (TEMPLATE_NAME_EXISTS)")
    public TemplateDto updateTemplate(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody TemplateRequest req) {
        return service.updateTemplate(me, id, req);
    }

    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un modèle", description = "Accès : ADMIN. Sans effet sur les parcours lancés.")
    @ApiResponse(responseCode = "204", description = "Modèle supprimé")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Modèle introuvable (NOT_FOUND)")
    public void deleteTemplate(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.deleteTemplate(me, id);
    }

    @PostMapping("/journeys")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Lancer un parcours", description = "Accès : ADMIN. Un seul parcours actif par arrivant ; le parrain est différent de l'arrivant et du manager. Les tâches du modèle sont copiées avec leurs échéances calculées.")
    @ApiResponse(responseCode = "201", description = "Parcours lancé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Modèle ou utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Parcours déjà en cours (ONBOARDING_ALREADY_ACTIVE)")
    public JourneyDto launch(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody JourneyRequest req) {
        return service.launch(me, req);
    }

    @GetMapping("/journeys")
    @Operation(summary = "Parcours", description = "Accès : authentifié. ADMIN : tous ; manager et parrain : leurs recrues. Filtres `status` (IN_PROGRESS, COMPLETED) et `overdue`.")
    @ApiResponse(responseCode = "200", description = "Parcours")
    @ApiResponse(responseCode = "401", description = U401)
    public List<JourneyDto> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String status,
                                 @RequestParam(required = false) Boolean overdue) {
        return service.list(me, status, overdue);
    }

    @GetMapping("/journeys/me")
    @Operation(summary = "Mon parcours", description = "Accès : arrivant. Le parcours le plus récent.")
    @ApiResponse(responseCode = "200", description = "Parcours")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Aucun parcours (NOT_FOUND)")
    public JourneyDto mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @GetMapping("/journeys/{id}")
    @Operation(summary = "Détail d'un parcours", description = "Accès : arrivant, manager, parrain ou ADMIN. Tâches, échéances, avancement.")
    @ApiResponse(responseCode = "200", description = "Parcours")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Parcours introuvable ou non accessible (NOT_FOUND)")
    public JourneyDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/journeys/{id}/tasks/{taskId}/done")
    @Operation(summary = "Cocher une tâche", description = "Accès : responsable de la tâche (IT et HR : ADMIN).")
    @ApiResponse(responseCode = "200", description = "Parcours")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le responsable de la tâche (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Parcours ou tâche introuvable (NOT_FOUND)")
    public JourneyDto done(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @PathVariable UUID taskId) {
        return service.toggle(me, id, taskId, true);
    }

    @DeleteMapping("/journeys/{id}/tasks/{taskId}/done")
    @Operation(summary = "Décocher une tâche", description = "Accès : responsable de la tâche (IT et HR : ADMIN).")
    @ApiResponse(responseCode = "200", description = "Parcours")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le responsable de la tâche (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Parcours ou tâche introuvable (NOT_FOUND)")
    public JourneyDto undone(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @PathVariable UUID taskId) {
        return service.toggle(me, id, taskId, false);
    }

    @GetMapping("/candidates")
    @Operation(summary = "Utilisateurs", description = "Accès : authentifié. Utilisateurs actifs (arrivant, manager, parrain).")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> candidates() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
