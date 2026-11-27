package fr.epita.platform.apps.volunteering.controller;

import fr.epita.platform.apps.volunteering.dto.VolunteeringDtos.*;
import fr.epita.platform.apps.volunteering.entity.VolunteerStatus;
import fr.epita.platform.apps.volunteering.service.VolunteeringService;
import fr.epita.platform.common.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Bénévolat", description = "Missions solidaires auprès d'associations partenaires, validation manager, plafond annuel et bilan.")
@RestController
@RequestMapping("/api/volunteering")
@RequiredArgsConstructor
public class VolunteeringController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String M404 = "Mission introuvable (NOT_FOUND)";
    private static final String R404 = "Inscription introuvable (NOT_FOUND)";

    private final VolunteeringService service;

    @GetMapping("/missions")
    @Operation(summary = "Missions à venir", description = "Accès : authentifié. Associations actives uniquement ; filtres par cause et période.")
    @ApiResponse(responseCode = "200", description = "Missions")
    @ApiResponse(responseCode = "400", description = "Date invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<MissionDto> missions(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String cause,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.missions(me, cause, from, to);
    }

    @GetMapping("/missions/{id}")
    @Operation(summary = "Détail d'une mission", description = "Accès : authentifié. Places restantes et mon statut.")
    @ApiResponse(responseCode = "200", description = "Mission")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = M404)
    public MissionDto mission(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.mission(me, id);
    }

    @PostMapping("/missions")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une mission", description = "Accès : ADMIN. Association active, date dans le futur.")
    @ApiResponse(responseCode = "201", description = "Mission créée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Association introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Association désactivée (ASSOCIATION_INACTIVE)")
    public MissionDto createMission(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody MissionRequest req) {
        return service.createMission(me, req);
    }

    @GetMapping("/associations")
    @Operation(summary = "Associations", description = "Accès : authentifié. Actives (toutes pour un ADMIN).")
    @ApiResponse(responseCode = "200", description = "Associations")
    @ApiResponse(responseCode = "401", description = U401)
    public List<AssociationDto> associations(@AuthenticationPrincipal AuthUser me) {
        return service.associations(me);
    }

    @PostMapping("/associations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une association", description = "Accès : ADMIN. Nom unique.")
    @ApiResponse(responseCode = "201", description = "Association")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Nom existant (ASSOCIATION_EXISTS)")
    public AssociationDto addAssociation(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AssociationRequest req) {
        return service.addAssociation(me, req);
    }

    @PutMapping("/associations/{id}")
    @Operation(summary = "Modifier ou désactiver une association", description = "Accès : ADMIN. Désactivée, elle ne propose plus de mission.")
    @ApiResponse(responseCode = "200", description = "Association")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Association introuvable (NOT_FOUND)")
    public AssociationDto updateAssociation(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody AssociationRequest req) {
        return service.updateAssociation(me, id, req);
    }

    @PostMapping("/missions/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "S'inscrire", description = "Accès : authentifié. Demande à valider par le manager ; places et plafond annuel (14 h) vérifiés.")
    @ApiResponse(responseCode = "201", description = "Inscription demandée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = M404)
    @ApiResponse(responseCode = "409", description = "MISSION_FULL, ALREADY_REGISTERED, YEARLY_LIMIT_REACHED ou MISSION_CLOSED")
    public RegistrationDto register(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.register(me, id);
    }

    @DeleteMapping("/missions/{id}/registrations/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Se désinscrire", description = "Accès : inscrit, avant la date de la mission.")
    @ApiResponse(responseCode = "204", description = "Désinscrit")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Mission ou inscription introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Mission passée (MISSION_CLOSED)")
    public void unregister(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.unregister(me, id);
    }

    @GetMapping("/team/registrations")
    @Operation(summary = "Inscriptions à valider", description = "Accès : manager (son équipe, relations `/admin/reports`) ; un ADMIN sans équipe voit tout. Par défaut status=REQUESTED.")
    @ApiResponse(responseCode = "200", description = "Inscriptions")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<RegistrationDto> team(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) VolunteerStatus status) {
        return service.team(me, status);
    }

    @PostMapping("/registrations/{id}/approve")
    @Operation(summary = "Accepter", description = "Accès : manager du salarié ou ADMIN. Plafond annuel revérifié.")
    @ApiResponse(responseCode = "200", description = "Inscription approuvée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le manager (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "YEARLY_LIMIT_REACHED ou INVALID_STATUS_TRANSITION")
    public RegistrationDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.approve(me, id);
    }

    @PostMapping("/registrations/{id}/reject")
    @Operation(summary = "Refuser", description = "Accès : manager du salarié ou ADMIN. Motif obligatoire.")
    @ApiResponse(responseCode = "200", description = "Inscription refusée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le manager (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "Déjà décidée (INVALID_STATUS_TRANSITION)")
    public RegistrationDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        return service.reject(me, id, req.reason());
    }

    @PostMapping("/registrations/{id}/hours")
    @Operation(summary = "Déclarer mes heures", description = "Accès : bénévole, inscription approuvée, après la date de la mission ; plafonnées à sa durée (par demi-heure).")
    @ApiResponse(responseCode = "200", description = "Inscription")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "MISSION_NOT_DONE ou INVALID_STATUS_TRANSITION")
    public RegistrationDto hours(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody HoursRequest req) {
        return service.declareHours(me, id, req);
    }

    @GetMapping("/me")
    @Operation(summary = "Mes missions et mon compteur", description = "Accès : authentifié. Heures engagées et déclarées de l'année, plafond 14 h.")
    @ApiResponse(responseCode = "200", description = "Compteur et inscriptions")
    @ApiResponse(responseCode = "401", description = U401)
    public MeDto me(@AuthenticationPrincipal AuthUser me) {
        return service.me(me);
    }

    @GetMapping("/admin/report")
    @Operation(summary = "Bilan annuel", description = "Accès : ADMIN. Heures déclarées par salarié, par association et au total.")
    @ApiResponse(responseCode = "200", description = "Bilan")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public ReportDto report(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Integer year) {
        return service.report(me, year);
    }
}
