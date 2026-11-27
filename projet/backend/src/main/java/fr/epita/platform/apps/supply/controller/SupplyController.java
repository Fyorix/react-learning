package fr.epita.platform.apps.supply.controller;

import fr.epita.platform.apps.supply.dto.SupplyDtos.*;
import fr.epita.platform.apps.supply.entity.RequestStatus;
import fr.epita.platform.apps.supply.service.SupplyService;
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

@Tag(name = "Fournitures", description = "Catalogue de fournitures, demandes, préparation, stock et réapprovisionnements.")
@RestController
@RequestMapping("/api/supplies")
@RequiredArgsConstructor
public class SupplyController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String R404 = "Demande introuvable (NOT_FOUND)";
    private static final String I404 = "Article introuvable (NOT_FOUND)";
    private static final String TRANSITION = "Statut incompatible (INVALID_STATUS_TRANSITION)";

    private final SupplyService service;

    @GetMapping("/items")
    @Operation(summary = "Catalogue", description = "Accès : authentifié. Stock disponible (stock − réservé) et quota restant ce mois-ci ; articles inactifs visibles des ADMIN seulement.")
    @ApiResponse(responseCode = "200", description = "Articles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ItemDto> items(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) String category,
                               @RequestParam(required = false) String search) {
        return service.catalog(me, category, search);
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un article", description = "Accès : ADMIN. Nom unique.")
    @ApiResponse(responseCode = "201", description = "Article créé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Nom existant (ITEM_EXISTS)")
    public ItemDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ItemRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/items/{id}")
    @Operation(summary = "Modifier un article", description = "Accès : ADMIN. Le champ `stock` est ignoré : le stock évolue par réapprovisionnements et remises.")
    @ApiResponse(responseCode = "200", description = "Article")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = I404)
    @ApiResponse(responseCode = "409", description = "Nom existant (ITEM_EXISTS)")
    public ItemDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ItemRequest req) {
        return service.update(me, id, req);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Demander", description = "Accès : authentifié. Quantités ≤ stock disponible ; quota mensuel par personne respecté.")
    @ApiResponse(responseCode = "201", description = "Demande en attente")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = I404)
    @ApiResponse(responseCode = "409", description = "INSUFFICIENT_STOCK ou QUOTA_EXCEEDED")
    public RequestDto request(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SupplyRequestBody req) {
        return service.request(me, req);
    }

    @GetMapping("/requests/me")
    @Operation(summary = "Mes demandes", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Demandes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<RequestDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @DeleteMapping("/requests/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Annuler ma demande", description = "Accès : demandeur, tant que la demande n'est pas remise (la réservation éventuelle est libérée).")
    @ApiResponse(responseCode = "204", description = "Demande annulée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "REQUEST_DELIVERED ou INVALID_STATUS_TRANSITION")
    public void cancel(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.cancel(me, id);
    }

    @GetMapping("/admin/requests")
    @Operation(summary = "Demandes à préparer", description = "Accès : ADMIN. Filtre facultatif par statut ; plus anciennes d'abord.")
    @ApiResponse(responseCode = "200", description = "Demandes")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public List<RequestDto> adminRequests(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) RequestStatus status) {
        return service.adminList(me, status);
    }

    @PostMapping("/requests/{id}/approve")
    @Operation(summary = "Valider", description = "Accès : ADMIN. Revérifie stock disponible et quota, puis réserve le stock.")
    @ApiResponse(responseCode = "200", description = "Demande validée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = "INSUFFICIENT_STOCK, QUOTA_EXCEEDED ou INVALID_STATUS_TRANSITION")
    public RequestDto approve(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.approve(me, id);
    }

    @PostMapping("/requests/{id}/reject")
    @Operation(summary = "Refuser", description = "Accès : ADMIN. Demande en attente ou validée (réservation libérée).")
    @ApiResponse(responseCode = "200", description = "Demande refusée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = TRANSITION)
    public RequestDto reject(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RejectRequest req) {
        return service.reject(me, id, req.reason());
    }

    @PostMapping("/requests/{id}/deliver")
    @Operation(summary = "Remettre", description = "Accès : ADMIN. Demande validée : le stock est décrémenté.")
    @ApiResponse(responseCode = "200", description = "Demande remise")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = R404)
    @ApiResponse(responseCode = "409", description = TRANSITION)
    public RequestDto deliver(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.deliver(me, id);
    }

    @PostMapping("/items/{id}/restock")
    @Operation(summary = "Réapprovisionner", description = "Accès : ADMIN. Ajoute la quantité au stock.")
    @ApiResponse(responseCode = "200", description = "Article")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = I404)
    public ItemDto restock(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody RestockRequest req) {
        return service.restock(me, id, req.quantity());
    }

    @GetMapping("/admin/alerts")
    @Operation(summary = "Alertes de stock", description = "Accès : ADMIN. Articles actifs dont le disponible est sous le seuil.")
    @ApiResponse(responseCode = "200", description = "Articles en alerte")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public List<ItemDto> alerts(@AuthenticationPrincipal AuthUser me) {
        return service.alerts(me);
    }

    @GetMapping("/admin/consumption")
    @Operation(summary = "Consommation", description = "Accès : ADMIN. Quantités remises entre `from` et `to` (par défaut le mois en cours), par article et par équipe (manager du demandeur).")
    @ApiResponse(responseCode = "200", description = "Consommation")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    public ConsumptionDto consumption(@AuthenticationPrincipal AuthUser me,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.consumption(me, from, to);
    }
}
