package fr.epita.platform.apps.quote.controller;

import fr.epita.platform.apps.quote.dto.QuoteDtos.ClientDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.ProductDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.ProductRequest;
import fr.epita.platform.apps.quote.dto.QuoteDtos.QuoteDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.QuoteRequest;
import fr.epita.platform.apps.quote.dto.QuoteDtos.QuoteStatsDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.QuoteSummaryDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.RefuseRequest;
import fr.epita.platform.apps.quote.dto.QuoteDtos.SendRequest;
import fr.epita.platform.apps.quote.entity.QuoteStatus;
import fr.epita.platform.apps.quote.service.QuoteService;
import fr.epita.platform.common.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Tag(name = "Devis", description = "Catalogue de produits et devis envoyés aux clients, acceptés ou refusés.")
@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
public class QuoteController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String SALES403 = "Réservé au rôle SALES ou aux ADMIN (FORBIDDEN)";

    private final QuoteService service;

    @GetMapping("/products")
    @Operation(summary = "Catalogue", description = "Accès : authentifié. Filtres `search` (nom ou référence), `category` ; `archived=true` pour les archivés (actifs par défaut).")
    @ApiResponse(responseCode = "200", description = "Produits")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ProductDto> products(@RequestParam(required = false) String search,
                                     @RequestParam(required = false) String category,
                                     @RequestParam(required = false) Boolean archived) {
        return service.products(search, category, archived);
    }

    @PostMapping("/products")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter un produit", description = "Accès : ADMIN. Référence unique ; TVA 0, 5.5, 10 ou 20 %.")
    @ApiResponse(responseCode = "201", description = "Produit ajouté")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "409", description = "Référence déjà utilisée (PRODUCT_REFERENCE_EXISTS)")
    public ProductDto createProduct(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ProductRequest req) {
        return service.createProduct(me, req);
    }

    @PutMapping("/products/{id}")
    @Operation(summary = "Modifier un produit", description = "Accès : ADMIN. Les devis déjà envoyés gardent les prix copiés à l'envoi ; les brouillons utilisent le nouveau prix.")
    @ApiResponse(responseCode = "200", description = "Produit modifié")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Produit introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Référence déjà utilisée (PRODUCT_REFERENCE_EXISTS)")
    public ProductDto updateProduct(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                    @Valid @RequestBody ProductRequest req) {
        return service.updateProduct(me, id, req);
    }

    @PostMapping("/products/{id}/archive")
    @Operation(summary = "Archiver / désarchiver un produit", description = "Accès : ADMIN. Bascule l'archivage. Un produit archivé n'est plus proposé mais reste affiché dans les anciens devis.")
    @ApiResponse(responseCode = "200", description = "Produit")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Produit introuvable (NOT_FOUND)")
    public ProductDto archive(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.toggleArchive(me, id);
    }

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. Rôles propres (SALES) ; un ADMIN les a tous. Sert au front pour afficher les pages commerciales.")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @GetMapping("/clients")
    @Operation(summary = "Clients possibles", description = "Accès : SALES. Utilisateurs actifs, hors soi-même (ajout au §5.12 : liste de choix du client).")
    @ApiResponse(responseCode = "200", description = "Clients")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SALES403)
    public List<ClientDto> clients(@AuthenticationPrincipal AuthUser me) {
        return service.clients(me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un devis", description = "Accès : SALES. Le devis est créé en brouillon (DRAFT). Le client est un autre utilisateur ; les produits ne sont pas archivés. Les totaux sont calculés par le serveur.")
    @ApiResponse(responseCode = "201", description = "Brouillon créé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SALES403)
    @ApiResponse(responseCode = "404", description = "Client ou produit introuvable (NOT_FOUND)")
    public QuoteDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody QuoteRequest req) {
        return service.create(me, req);
    }

    @GetMapping
    @Operation(summary = "Lister les devis", description = "Accès : authentifié. SALES : ses devis ; ADMIN : tous ; autre utilisateur : les devis envoyés qui lui sont adressés. Filtres `status` (EXPIRED = envoyé et validité dépassée) et `clientId`.")
    @ApiResponse(responseCode = "200", description = "Devis, du plus récent au plus ancien")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<QuoteSummaryDto> list(@AuthenticationPrincipal AuthUser me,
                                      @RequestParam(required = false) QuoteStatus status,
                                      @RequestParam(required = false) UUID clientId) {
        return service.list(me, status, clientId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un devis", description = "Accès : commercial du devis, ADMIN, ou client destinataire une fois le devis envoyé. Lignes, totaux HT, TVA par taux et TTC.")
    @ApiResponse(responseCode = "200", description = "Devis")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Devis introuvable ou non accessible (NOT_FOUND)")
    public QuoteDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un brouillon", description = "Accès : commercial du devis ou ADMIN. Seul un devis DRAFT est modifiable ; les lignes sont remplacées.")
    @ApiResponse(responseCode = "200", description = "Brouillon modifié")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le commercial du devis (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Devis, client ou produit introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Devis déjà envoyé (QUOTE_LOCKED)")
    public QuoteDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                           @Valid @RequestBody QuoteRequest req) {
        return service.update(me, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un brouillon", description = "Accès : commercial du devis ou ADMIN. Seul un devis DRAFT peut être supprimé.")
    @ApiResponse(responseCode = "204", description = "Brouillon supprimé")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le commercial du devis (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Devis introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Devis déjà envoyé (QUOTE_LOCKED)")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dupliquer un devis", description = "Accès : commercial du devis ou ADMIN. Crée un brouillon avec les mêmes lignes, aux prix actuels du catalogue ; les produits archivés sont retirés.")
    @ApiResponse(responseCode = "201", description = "Nouveau brouillon")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SALES403)
    @ApiResponse(responseCode = "404", description = "Devis introuvable (NOT_FOUND)")
    public QuoteDto duplicate(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.duplicate(me, id);
    }

    @PostMapping("/{id}/send")
    @Operation(summary = "Envoyer un devis", description = "Accès : commercial du devis ou ADMIN. Brouillon non vide ; `validUntil` dans le futur. Les prix, libellés et taux sont copiés dans les lignes et un numéro continu est attribué (DEV-2026-0001).")
    @ApiResponse(responseCode = "200", description = "Devis envoyé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le commercial du devis (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Devis introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "QUOTE_EMPTY ou QUOTE_LOCKED")
    public QuoteDto send(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody SendRequest req) {
        return service.send(me, id, req.validUntil());
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Accepter un devis", description = "Accès : client destinataire. Devis SENT et non expiré.")
    @ApiResponse(responseCode = "200", description = "Devis accepté")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le client destinataire (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Devis introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "QUOTE_EXPIRED ou QUOTE_LOCKED (déjà répondu)")
    public QuoteDto accept(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.accept(me, id);
    }

    @PostMapping("/{id}/refuse")
    @Operation(summary = "Refuser un devis", description = "Accès : client destinataire. Devis SENT et non expiré ; motif obligatoire.")
    @ApiResponse(responseCode = "200", description = "Devis refusé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le client destinataire (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Devis introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "QUOTE_EXPIRED ou QUOTE_LOCKED (déjà répondu)")
    public QuoteDto refuse(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                           @Valid @RequestBody RefuseRequest req) {
        return service.refuse(me, id, req.comment());
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques commerciales", description = "Accès : SALES (ses devis) ou ADMIN (tous). `month` au format AAAA-MM (mois courant par défaut) : devis créés par statut, montant HT accepté et taux de transformation des devis envoyés dans le mois.")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "400", description = "Mois invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = SALES403)
    public QuoteStatsDto stats(@AuthenticationPrincipal AuthUser me,
                               @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return service.stats(me, month);
    }
}
