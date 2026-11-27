package fr.epita.platform.apps.license.controller;

import fr.epita.platform.apps.license.dto.LicenseDtos.*;
import fr.epita.platform.apps.license.entity.LicenseRequestStatus;
import fr.epita.platform.apps.license.service.LicenseService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.user.dto.UserDtos.UserSummary;
import fr.epita.platform.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Licences", description = "Abonnements logiciels : sièges, demandes de licence, alertes et coûts.")
@RestController
@RequestMapping("/api/licenses")
@RequiredArgsConstructor
public class LicenseController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String S404 = "Logiciel introuvable (NOT_FOUND)";

    private final LicenseService service;
    private final UserRepository users;

    @GetMapping("/software")
    @Operation(summary = "Catalogue", description = "Accès : authentifié. Logiciels actifs (tous pour un ADMIN), sièges restants, « j'ai une licence ».")
    @ApiResponse(responseCode = "200", description = "Logiciels")
    @ApiResponse(responseCode = "401", description = U401)
    public List<SoftwareDto> catalog(@AuthenticationPrincipal AuthUser me) {
        return service.catalog(me);
    }

    @PostMapping("/software")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un logiciel", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "201", description = "Logiciel")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Nom existant (SOFTWARE_EXISTS)")
    public SoftwareDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SoftwareRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/software/{id}")
    @Operation(summary = "Modifier ou résilier", description = "Accès : ADMIN. `seats` jamais sous le nombre de sièges attribués ; `status: TERMINATED` résilie.")
    @ApiResponse(responseCode = "200", description = "Logiciel")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = S404)
    @ApiResponse(responseCode = "409", description = "SEATS_BELOW_ASSIGNED ou SOFTWARE_EXISTS")
    public SoftwareDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody SoftwareRequest req) {
        return service.update(me, id, req);
    }

    @GetMapping("/software/{id}/seats")
    @Operation(summary = "Détenteurs", description = "Accès : ADMIN. Avec la dernière utilisation déclarée.")
    @ApiResponse(responseCode = "200", description = "Sièges")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = S404)
    public List<SeatDto> seats(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.holders(me, id);
    }

    @PostMapping("/software/{id}/seats")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attribuer un siège", description = "Accès : ADMIN. Sous verrou du logiciel : jamais plus de sièges que `seats`.")
    @ApiResponse(responseCode = "201", description = "Siège")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Logiciel ou utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "NO_SEAT_LEFT, ALREADY_ASSIGNED ou SOFTWARE_TERMINATED")
    public SeatDto assign(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody AssignRequest req) {
        return service.assign(me, id, req.userId());
    }

    @DeleteMapping("/software/{id}/seats/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Retirer un siège", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "204", description = "Siège libéré")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Logiciel ou siège introuvable (NOT_FOUND)")
    public void unassign(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @PathVariable UUID userId) {
        service.unassign(me, id, userId);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Demander une licence", description = "Accès : authentifié. Logiciel actif, pas de siège ni de demande en attente.")
    @ApiResponse(responseCode = "201", description = "Demande")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = S404)
    @ApiResponse(responseCode = "409", description = "SOFTWARE_TERMINATED, ALREADY_ASSIGNED ou REQUEST_PENDING")
    public LicenseRequestDto request(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AskLicenseRequest req) {
        return service.request(me, req);
    }

    @GetMapping("/requests")
    @Operation(summary = "Demandes", description = "Accès : authentifié. Les miennes (toutes pour un ADMIN) ; filtre par statut.")
    @ApiResponse(responseCode = "200", description = "Demandes")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<LicenseRequestDto> requests(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) LicenseRequestStatus status) {
        return service.requests(me, status);
    }

    @PostMapping("/requests/{id}/approve")
    @Operation(summary = "Accepter une demande", description = "Accès : ADMIN. Attribution immédiate du siège.")
    @ApiResponse(responseCode = "200", description = "Demande acceptée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Demande introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "NO_SEAT_LEFT, ALREADY_ASSIGNED, SOFTWARE_TERMINATED ou INVALID_STATUS_TRANSITION")
    public LicenseRequestDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.approve(me, id);
    }

    @PostMapping("/requests/{id}/reject")
    @Operation(summary = "Refuser une demande", description = "Accès : ADMIN. Motif obligatoire.")
    @ApiResponse(responseCode = "200", description = "Demande refusée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Demande introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà décidée (INVALID_STATUS_TRANSITION)")
    public LicenseRequestDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        return service.reject(me, id, req.reason());
    }

    @GetMapping("/me")
    @Operation(summary = "Mes licences", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Licences")
    @ApiResponse(responseCode = "401", description = U401)
    public List<MyLicenseDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @PostMapping("/me/{softwareId}/usage")
    @Operation(summary = "Déclarer une utilisation", description = "Accès : détenteur. Met à jour la date de dernière utilisation (alerte « inutilisé depuis 90 jours »).")
    @ApiResponse(responseCode = "200", description = "Licence")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Licence introuvable (NOT_FOUND)")
    public MyLicenseDto usage(@AuthenticationPrincipal AuthUser me, @PathVariable UUID softwareId) {
        return service.declareUsage(me, softwareId);
    }

    @GetMapping("/admin/alerts")
    @Operation(summary = "Alertes", description = "Accès : ADMIN. Renouvellements dans moins de 30 jours, sièges non utilisés depuis 90 jours.")
    @ApiResponse(responseCode = "200", description = "Alertes")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public AlertsDto alerts(@AuthenticationPrincipal AuthUser me) {
        return service.alerts(me);
    }

    @GetMapping("/admin/costs")
    @Operation(summary = "Coûts mensuels", description = "Accès : ADMIN. Sièges attribués × coût ramené au mois, par logiciel et par équipe (manager du détenteur).")
    @ApiResponse(responseCode = "200", description = "Coûts")
    @ApiResponse(responseCode = "400", description = "Mois invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public CostsDto costs(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.costs(me, month);
    }

    @GetMapping("/people")
    @Operation(summary = "Salariés", description = "Accès : authentifié. Utilisateurs actifs, pour attribuer un siège.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
