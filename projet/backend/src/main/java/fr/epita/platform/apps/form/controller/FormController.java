package fr.epita.platform.apps.form.controller;

import fr.epita.platform.apps.form.dto.FormDtos.*;
import fr.epita.platform.apps.form.service.FormService;
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
import java.util.Map;
import java.util.UUID;

@Tag(name = "Formulaires", description = "Constructeur de formulaires dynamiques : structure versionnée, validation serveur, réponses et statistiques.")
@RestController
@RequestMapping("/api/forms")
@RequiredArgsConstructor
public class FormController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String OWN = "Réservé au créateur du formulaire ou à un ADMIN (FORBIDDEN)";
    private static final String F404 = "Formulaire introuvable (NOT_FOUND)";

    private final FormService service;

    @GetMapping("/field-types")
    @Operation(summary = "Types de champ", description = "Accès : authentifié. Types disponibles, clés de `config` acceptées et forme de la réponse attendue.")
    @ApiResponse(responseCode = "200", description = "Types")
    @ApiResponse(responseCode = "401", description = U401)
    public List<FieldTypeDto> fieldTypes() {
        return service.fieldTypes();
    }

    @GetMapping
    @Operation(summary = "Formulaires", description = "Accès : authentifié. `mine=true` : mes formulaires (tous statuts) ; sinon les formulaires publiés.")
    @ApiResponse(responseCode = "200", description = "Formulaires")
    @ApiResponse(responseCode = "401", description = U401)
    public List<FormSummary> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Boolean mine) {
        return service.list(me, mine);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un brouillon", description = "Accès : authentifié (devient le créateur). Version 1, sans champ.")
    @ApiResponse(responseCode = "201", description = "Formulaire")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public FormDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody FormRequest req) {
        return service.create(me, req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Formulaire et structure", description = "Accès : authentifié. Le créateur voit son brouillon s'il existe ; les autres, la version publiée.")
    @ApiResponse(responseCode = "200", description = "Formulaire")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = F404)
    public FormDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PutMapping("/{id}/structure")
    @Operation(summary = "Enregistrer la structure", description = "Accès : créateur, brouillon uniquement. Configuration incohérente refusée avec le chemin du champ fautif (ex. `fields[2].config.max`) ; condition sur un champ placé avant.")
    @ApiResponse(responseCode = "200", description = "Formulaire")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = F404)
    @ApiResponse(responseCode = "409", description = "Version publiée non modifiable (FORM_PUBLISHED)")
    public FormDto structure(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody StructureRequest req) {
        return service.saveStructure(me, id, req);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "Publier", description = "Accès : créateur. Le brouillon devient la version publiée (au moins un champ).")
    @ApiResponse(responseCode = "200", description = "Formulaire publié")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = F404)
    @ApiResponse(responseCode = "409", description = "FORM_PUBLISHED (aucun brouillon) ou FORM_CLOSED")
    public FormDto publish(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.publish(me, id);
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nouvelle version", description = "Accès : créateur. Brouillon copié de la version publiée ; les réponses existantes restent rattachées à leur version.")
    @ApiResponse(responseCode = "201", description = "Formulaire (brouillon)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = F404)
    @ApiResponse(responseCode = "409", description = "DRAFT_EXISTS ou FORM_NOT_PUBLISHED")
    public FormDto newVersion(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.newVersion(me, id);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Clore", description = "Accès : créateur. Plus aucune réponse acceptée.")
    @ApiResponse(responseCode = "200", description = "Formulaire clos")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = F404)
    @ApiResponse(responseCode = "409", description = "Pas publié (INVALID_STATUS_TRANSITION)")
    public FormDto close(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.close(me, id);
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Valider sans enregistrer", description = "Accès : authentifié. Aperçu : erreurs par champ (`answers.<fieldId>`) et champs masqués par leur condition.")
    @ApiResponse(responseCode = "200", description = "Résultat de validation")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = F404)
    public ValidationResult validate(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody AnswersRequest req) {
        return service.validate(me, id, req);
    }

    @PostMapping("/{id}/responses")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Répondre", description = "Accès : authentifié. Validation serveur par champ ; champs masqués ignorés ; anonyme : auteur non enregistré.")
    @ApiResponse(responseCode = "201", description = "Valeurs enregistrées")
    @ApiResponse(responseCode = "400", description = "Réponses invalides, un message par champ (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = F404)
    @ApiResponse(responseCode = "409", description = "ALREADY_RESPONDED ou FORM_CLOSED")
    public Map<String, Object> respond(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody AnswersRequest req) {
        return service.respond(me, id, req);
    }

    @GetMapping("/{id}/responses")
    @Operation(summary = "Réponses", description = "Accès : créateur ou ADMIN. Paginées, d'une version (publiée par défaut).")
    @ApiResponse(responseCode = "200", description = "Page de réponses")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = "Formulaire ou version introuvable (NOT_FOUND)")
    public PageResponse<ResponseDto> responses(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                               @RequestParam(required = false) Integer version,
                                               @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.responses(me, id, version, page, size);
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "Statistiques", description = "Accès : créateur ou ADMIN. Par champ : répartition (choix, notes), moyenne / min / max (nombres, notes), extraits (textes).")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWN)
    @ApiResponse(responseCode = "404", description = "Formulaire ou version introuvable (NOT_FOUND)")
    public StatsDto stats(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @RequestParam(required = false) Integer version) {
        return service.stats(me, id, version);
    }
}
