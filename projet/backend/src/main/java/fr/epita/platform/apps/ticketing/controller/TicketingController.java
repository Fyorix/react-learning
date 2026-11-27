package fr.epita.platform.apps.ticketing.controller;

import fr.epita.platform.apps.ticketing.dto.TicketingDtos.*;
import fr.epita.platform.apps.ticketing.service.TicketingService;
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

@Tag(name = "Billetterie", description = "Événements, tarifs et quotas, commandes, billets à code unique, contrôle à l'entrée.")
@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketingController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String E404 = "Événement introuvable (NOT_FOUND)";
    private static final String ORG = "Réservé à l'organisateur (FORBIDDEN)";

    private final TicketingService service;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. SCANNER le cas échéant (un ADMIN l'a d'office).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @GetMapping("/events")
    @Operation(summary = "Événements", description = "Accès : authentifié. À venir par défaut ; `upcoming=false` : tous.")
    @ApiResponse(responseCode = "200", description = "Événements")
    @ApiResponse(responseCode = "401", description = U401)
    public List<EventDto> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Boolean upcoming) {
        return service.list(me, upcoming);
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un événement", description = "Accès : authentifié (devient organisateur). Somme des quotas ≤ capacité.")
    @ApiResponse(responseCode = "201", description = "Événement")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public EventDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody EventRequest req) {
        return service.create(me, req);
    }

    @GetMapping("/events/{id}")
    @Operation(summary = "Détail", description = "Accès : authentifié. Tarifs et places restantes par tarif.")
    @ApiResponse(responseCode = "200", description = "Événement")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = E404)
    public EventDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PutMapping("/events/{id}")
    @Operation(summary = "Modifier", description = "Accès : organisateur, ADMIN. Quotas et jauge jamais sous le vendu ; un tarif vendu n'est pas supprimable.")
    @ApiResponse(responseCode = "200", description = "Événement")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ORG)
    @ApiResponse(responseCode = "404", description = E404)
    @ApiResponse(responseCode = "409", description = "Sous le vendu (QUOTA_BELOW_SOLD)")
    public EventDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody EventRequest req) {
        return service.update(me, id, req);
    }

    @PostMapping("/events/{id}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Acheter", description = "Accès : authentifié. 6 billets maximum ; quotas et jauge garantis même en achats simultanés ; paiement fictif.")
    @ApiResponse(responseCode = "201", description = "Commande payée et billets")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = E404)
    @ApiResponse(responseCode = "409", description = "ORDER_LIMIT, SOLD_OUT ou SALES_CLOSED")
    public OrderDto order(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody OrderRequest req) {
        return service.order(me, id, req.lines());
    }

    @GetMapping("/me/orders")
    @Operation(summary = "Mes commandes et billets", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Commandes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<OrderDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @PostMapping("/orders/{id}/refund")
    @Operation(summary = "Rembourser", description = "Accès : acheteur. Jusqu'à 48 h avant l'événement, aucun billet contrôlé.")
    @ApiResponse(responseCode = "200", description = "Commande REFUNDED")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Commande introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "REFUND_CLOSED, TICKET_SCANNED ou INVALID_STATUS_TRANSITION")
    public OrderDto refund(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.refund(me, id);
    }

    @PostMapping("/events/{id}/scan")
    @Operation(summary = "Contrôler un billet", description = "Accès : SCANNER ou organisateur. Valide une seule fois.")
    @ApiResponse(responseCode = "200", description = "Billet valide")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Ni SCANNER ni organisateur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Code inconnu ou événement introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "ALREADY_SCANNED (avec l'heure du premier passage), WRONG_EVENT ou TICKET_REFUNDED")
    public ScanResult scan(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ScanRequest req) {
        return service.scan(me, id, req.code());
    }

    @GetMapping("/events/{id}/dashboard")
    @Operation(summary = "Tableau de bord", description = "Accès : organisateur, ADMIN. Ventes par tarif, remplissage, entrées.")
    @ApiResponse(responseCode = "200", description = "Tableau de bord")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ORG)
    @ApiResponse(responseCode = "404", description = E404)
    public DashboardDto dashboard(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.dashboard(me, id);
    }
}
