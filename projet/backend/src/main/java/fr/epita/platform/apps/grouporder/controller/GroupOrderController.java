package fr.epita.platform.apps.grouporder.controller;

import fr.epita.platform.apps.grouporder.dto.GroupOrderDtos.*;
import fr.epita.platform.apps.grouporder.entity.OrderStatus;
import fr.epita.platform.apps.grouporder.service.GroupOrderService;
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

@Tag(name = "Commande groupée", description = "Commandes collectives chez un fournisseur, avec total par personne et suivi des paiements.")
@RestController
@RequestMapping("/api/group-orders")
@RequiredArgsConstructor
public class GroupOrderController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";
    private static final String ORG = "Réservé à l'organisateur (FORBIDDEN)";
    private static final String N404 = "Commande introuvable (NOT_FOUND)";

    private final GroupOrderService service;

    @GetMapping("/suppliers")
    @Operation(summary = "Fournisseurs et cartes", description = "Accès : authentifié. Les articles archivés ne sont pas renvoyés.")
    @ApiResponse(responseCode = "200", description = "Fournisseurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<SupplierDto> suppliers() {
        return service.suppliers();
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un fournisseur", description = "Accès : ADMIN. Nom unique ; la carte est vide à la création.")
    @ApiResponse(responseCode = "201", description = "Fournisseur créé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Nom existant (SUPPLIER_EXISTS)")
    public SupplierDto createSupplier(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody SupplierRequest req) {
        return service.createSupplier(me, req);
    }

    @PutMapping("/suppliers/{id}/menu")
    @Operation(summary = "Remplacer la carte", description = "Accès : ADMIN. Les articles transmis avec `id` sont mis à jour, sans `id` créés ; les absents sont archivés. Les commandes en cours gardent leurs prix figés.")
    @ApiResponse(responseCode = "200", description = "Fournisseur et nouvelle carte")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Fournisseur introuvable (NOT_FOUND)")
    public SupplierDto menu(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody MenuRequest req) {
        return service.replaceMenu(me, id, req);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ouvrir une commande", description = "Accès : authentifié (devient l'organisateur). Heure limite dans le futur.")
    @ApiResponse(responseCode = "201", description = "Commande ouverte")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Fournisseur introuvable (NOT_FOUND)")
    public OrderDto open(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody OrderRequest req) {
        return service.open(me, req);
    }

    @GetMapping
    @Operation(summary = "Commandes ouvertes et récentes", description = "Accès : authentifié. Sans `status` : commandes ouvertes et celles des 30 derniers jours.")
    @ApiResponse(responseCode = "200", description = "Commandes")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<OrderSummaryDto> list(@RequestParam(required = false) OrderStatus status) {
        return service.list(status);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une commande", description = "Accès : authentifié. Lignes par personne, part de livraison (au centime, reste à l'organisateur), total par personne et total général.")
    @ApiResponse(responseCode = "200", description = "Commande")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = N404)
    public OrderDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PutMapping("/{id}/my-lines")
    @Operation(summary = "Remplacer mes lignes", description = "Accès : authentifié. Avant l'heure limite et tant que la commande est ouverte. Liste vide : je me retire.")
    @ApiResponse(responseCode = "200", description = "Commande")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = N404)
    @ApiResponse(responseCode = "409", description = "ORDER_CLOSED ou ITEM_UNAVAILABLE")
    public OrderDto myLines(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody LinesRequest req) {
        return service.replaceMyLines(me, id, req);
    }

    @PostMapping("/{id}/close")
    @Operation(summary = "Clôturer", description = "Accès : organisateur. Refusé si le minimum du fournisseur n'est pas atteint. Ouvre le suivi des paiements.")
    @ApiResponse(responseCode = "200", description = "Commande clôturée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ORG)
    @ApiResponse(responseCode = "404", description = N404)
    @ApiResponse(responseCode = "409", description = "MINIMUM_NOT_REACHED ou INVALID_STATUS_TRANSITION")
    public OrderDto close(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.close(me, id);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Annuler", description = "Accès : organisateur. Commande ouverte ou clôturée.")
    @ApiResponse(responseCode = "200", description = "Commande annulée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ORG)
    @ApiResponse(responseCode = "404", description = N404)
    @ApiResponse(responseCode = "409", description = "Statut incompatible (INVALID_STATUS_TRANSITION)")
    public OrderDto cancel(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.cancel(me, id);
    }

    @PostMapping("/{id}/delivered")
    @Operation(summary = "Marquer livrée", description = "Accès : organisateur. Commande clôturée.")
    @ApiResponse(responseCode = "200", description = "Commande livrée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ORG)
    @ApiResponse(responseCode = "404", description = N404)
    @ApiResponse(responseCode = "409", description = "Statut incompatible (INVALID_STATUS_TRANSITION)")
    public OrderDto delivered(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.delivered(me, id);
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "Bon de commande", description = "Accès : authentifié. Quantités consolidées par article et options, sous-total, livraison, total.")
    @ApiResponse(responseCode = "200", description = "Bon de commande")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = N404)
    public SummaryDto summary(@PathVariable UUID id) {
        return service.summary(id);
    }

    @PutMapping("/{id}/payments/{userId}")
    @Operation(summary = "Marquer un paiement", description = "Accès : organisateur. Commande clôturée ou livrée ; l'utilisateur doit être participant.")
    @ApiResponse(responseCode = "200", description = "Commande")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ORG)
    @ApiResponse(responseCode = "404", description = "Commande ou participant introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Commande encore ouverte ou annulée (INVALID_STATUS_TRANSITION)")
    public OrderDto paid(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @PathVariable UUID userId,
                         @Valid @RequestBody PaymentRequest req) {
        return service.setPaid(me, id, userId, req.paid());
    }
}
