package fr.epita.platform.apps.invoice.controller;

import fr.epita.platform.apps.invoice.dto.InvoiceDtos.*;
import fr.epita.platform.apps.invoice.entity.InvoiceStatus;
import fr.epita.platform.apps.invoice.service.InvoiceService;
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

@Tag(name = "Facturation", description = "Factures numérotées, paiements, avoirs et relances des retards.")
@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
public class InvoiceController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ACC = "Réservé aux ACCOUNTANT (FORBIDDEN)";
    private static final String I404 = "Facture introuvable (NOT_FOUND)";

    private final InvoiceService service;
    private final UserRepository users;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. ACCOUNTANT le cas échéant (un ADMIN l'a d'office).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un brouillon", description = "Accès : ACCOUNTANT. Totaux calculés (HT et TVA arrondis au centime par ligne).")
    @ApiResponse(responseCode = "201", description = "Brouillon")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ACC)
    @ApiResponse(responseCode = "404", description = "Client introuvable (NOT_FOUND)")
    public InvoiceDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody InvoiceRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un brouillon", description = "Accès : ACCOUNTANT. Une facture émise n'est plus modifiable.")
    @ApiResponse(responseCode = "200", description = "Brouillon")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ACC)
    @ApiResponse(responseCode = "404", description = I404)
    @ApiResponse(responseCode = "409", description = "Facture émise (INVOICE_ISSUED)")
    public InvoiceDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody InvoiceRequest req) {
        return service.update(me, id, req);
    }

    @PostMapping("/{id}/issue")
    @Operation(summary = "Émettre", description = "Accès : ACCOUNTANT. Attribue le numéro FAC-AAAA-NNNN (continu par année, sans trou) ; la facture est figée.")
    @ApiResponse(responseCode = "200", description = "Facture émise")
    @ApiResponse(responseCode = "400", description = "Échéance passée (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ACC)
    @ApiResponse(responseCode = "404", description = I404)
    @ApiResponse(responseCode = "409", description = "Déjà émise (INVOICE_ISSUED)")
    public InvoiceDto issue(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.issue(me, id);
    }

    @GetMapping
    @Operation(summary = "Factures", description = "Accès : ACCOUNTANT (toutes, brouillons compris) ou client (ses factures émises). Filtres : statut calculé, client, en retard.")
    @ApiResponse(responseCode = "200", description = "Factures")
    @ApiResponse(responseCode = "400", description = "Paramètre invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<InvoiceSummary> list(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) InvoiceStatus status,
                                     @RequestParam(required = false) UUID clientId, @RequestParam(required = false) Boolean overdue) {
        return service.list(me, status, clientId, overdue);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'une facture", description = "Accès : ACCOUNTANT ou client destinataire (facture émise). Lignes, totaux, paiements, avoirs ; relances pour l'ACCOUNTANT.")
    @ApiResponse(responseCode = "200", description = "Facture")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = I404)
    public InvoiceDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/{id}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistrer un paiement", description = "Accès : ACCOUNTANT. Partiel autorisé ; paiements + avoirs ne dépassent jamais le TTC.")
    @ApiResponse(responseCode = "201", description = "Facture")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ACC)
    @ApiResponse(responseCode = "404", description = I404)
    @ApiResponse(responseCode = "409", description = "OVERPAYMENT ou INVOICE_NOT_ISSUED")
    public InvoiceDto pay(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody PaymentRequest req) {
        return service.pay(me, id, req);
    }

    @PostMapping("/{id}/credit-notes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un avoir", description = "Accès : ACCOUNTANT. Numéro AVO-AAAA-NNNN ; montant ≤ reste dû. Un avoir total annule la facture.")
    @ApiResponse(responseCode = "201", description = "Facture")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ACC)
    @ApiResponse(responseCode = "404", description = I404)
    @ApiResponse(responseCode = "409", description = "CREDIT_TOO_HIGH ou INVOICE_NOT_ISSUED")
    public InvoiceDto credit(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody CreditRequest req) {
        return service.credit(me, id, req);
    }

    @GetMapping("/reminders/due")
    @Operation(summary = "Relances à envoyer", description = "Accès : ACCOUNTANT. Factures en retard dont le plus haut niveau atteint (J+7, J+15, J+30) n'est pas encore envoyé.")
    @ApiResponse(responseCode = "200", description = "Relances")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ACC)
    public List<ReminderDueDto> remindersDue(@AuthenticationPrincipal AuthUser me) {
        return service.remindersDue(me);
    }

    @PostMapping("/{id}/reminders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistrer une relance", description = "Accès : ACCOUNTANT. Une relance par niveau, seulement une fois le délai atteint.")
    @ApiResponse(responseCode = "201", description = "Facture")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ACC)
    @ApiResponse(responseCode = "404", description = I404)
    @ApiResponse(responseCode = "409", description = "REMINDER_ALREADY_SENT, REMINDER_NOT_DUE ou INVOICE_NOT_ISSUED")
    public InvoiceDto remind(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ReminderRequest req) {
        return service.remind(me, id, req.level());
    }

    @GetMapping("/stats")
    @Operation(summary = "Tableau de bord", description = "Accès : ACCOUNTANT. Facturé et avoirs de l'année, encaissé dans l'année, restant dû et retards (toutes années).")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ACC)
    public StatsDto stats(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Integer year) {
        return service.stats(me, year);
    }

    @GetMapping("/clients")
    @Operation(summary = "Clients possibles", description = "Accès : authentifié. Utilisateurs actifs.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> clients() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
