package fr.epita.platform.apps.expense.controller;

import fr.epita.platform.apps.expense.dto.ExpenseDtos.ClaimDto;
import fr.epita.platform.apps.expense.dto.ExpenseDtos.ClaimSummaryDto;
import fr.epita.platform.apps.expense.dto.ExpenseDtos.ItemRequest;
import fr.epita.platform.apps.expense.dto.ExpenseDtos.ReviewRequest;
import fr.epita.platform.apps.expense.dto.ExpenseDtos.StatsDto;
import fr.epita.platform.apps.expense.entity.ExpenseEnums.ClaimStatus;
import fr.epita.platform.apps.expense.service.ExpenseService;
import fr.epita.platform.common.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Tag(name = "Notes de frais", description = "Dépenses avec justificatif, notes mensuelles validées par le manager et remboursées par l'admin.")
@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides (VALIDATION_ERROR) ou justificatif refusé : JPEG, PNG ou PDF, 5 Mo maximum (INVALID_FILE)";

    private final ExpenseService service;

    @PostMapping(value = "/items", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une dépense", description = "Accès : authentifié. Multipart : partie `data` (JSON) et fichier `receipt` obligatoire (JPEG, PNG ou PDF, 5 Mo maximum). La dépense est rangée dans la note du mois de sa date, créée en brouillon si besoin. Plafonds : repas 25 €, hôtel 120 € (au-delà, justification obligatoire) ; pas de dépense de plus de 3 mois.")
    @ApiResponse(responseCode = "201", description = "Note du mois avec la nouvelle dépense")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "409", description = "EXPENSE_TOO_OLD ou REPORT_LOCKED (note du mois déjà soumise)")
    public ClaimDto addItem(@AuthenticationPrincipal AuthUser me, @Valid @RequestPart("data") ItemRequest data,
                            @RequestPart("receipt") MultipartFile receipt) {
        return service.addItem(me, data, receipt);
    }

    @PutMapping(value = "/items/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Modifier une dépense", description = "Accès : auteur, note en brouillon. Multipart : `data` (JSON) et `receipt` facultatif (remplace le justificatif). Si la date change de mois, la dépense passe dans la note de ce mois.")
    @ApiResponse(responseCode = "200", description = "Note mise à jour")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas l'auteur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Dépense introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "REPORT_LOCKED ou EXPENSE_TOO_OLD")
    public ClaimDto updateItem(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                               @Valid @RequestPart("data") ItemRequest data,
                               @RequestPart(value = "receipt", required = false) MultipartFile receipt) {
        return service.updateItem(me, id, data, receipt);
    }

    @DeleteMapping("/items/{id}")
    @Operation(summary = "Supprimer une dépense", description = "Accès : auteur, note en brouillon.")
    @ApiResponse(responseCode = "200", description = "Note mise à jour")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas l'auteur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Dépense introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Note soumise (REPORT_LOCKED)")
    public ClaimDto deleteItem(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.deleteItem(me, id);
    }

    @GetMapping("/items/{id}/receipt")
    @Operation(summary = "Télécharger un justificatif", description = "Accès : auteur, son manager ou ADMIN.")
    @ApiResponse(responseCode = "200", description = "Fichier (image ou PDF)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Dépense ou fichier introuvable (NOT_FOUND)")
    public ResponseEntity<byte[]> receipt(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        ExpenseService.Receipt r = service.receipt(me, id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(r.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(r.fileName() == null ? "justificatif" : r.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(r.content());
    }

    @GetMapping("/reports/me")
    @Operation(summary = "Mes notes", description = "Accès : authentifié. Une note par mois.")
    @ApiResponse(responseCode = "200", description = "Notes, de la plus récente à la plus ancienne")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ClaimSummaryDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @GetMapping("/reports/{id}")
    @Operation(summary = "Détail d'une note", description = "Accès : auteur, son manager ou ADMIN.")
    @ApiResponse(responseCode = "200", description = "Note")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Note introuvable ou non accessible (NOT_FOUND)")
    public ClaimDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/reports/{id}/submit")
    @Operation(summary = "Soumettre une note", description = "Accès : auteur. Note en brouillon et non vide ; elle n'est plus modifiable ensuite.")
    @ApiResponse(responseCode = "200", description = "Note soumise")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas l'auteur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Note introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "REPORT_LOCKED ou REPORT_EMPTY")
    public ClaimDto submit(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.submit(me, id);
    }

    @GetMapping("/team/reports")
    @Operation(summary = "Notes de mes salariés", description = "Accès : manager (ADMIN : toutes). `status` : SUBMITTED pour les notes à valider (par défaut : toutes sauf les brouillons).")
    @ApiResponse(responseCode = "200", description = "Notes")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ClaimSummaryDto> team(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) ClaimStatus status) {
        return service.team(me, status);
    }

    @PostMapping("/reports/{id}/review")
    @Operation(summary = "Valider une note ligne par ligne", description = "Accès : manager du salarié ou ADMIN, jamais sa propre note. Une décision par ligne ; motif obligatoire pour une ligne refusée. La note est VALIDATED si au moins une ligne est acceptée, sinon REJECTED ; le remboursement = somme des lignes acceptées.")
    @ApiResponse(responseCode = "200", description = "Note revue")
    @ApiResponse(responseCode = "400", description = "Décision manquante ou motif absent (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le manager du salarié (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Note introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Note non soumise (INVALID_STATUS_TRANSITION)")
    public ClaimDto review(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ReviewRequest req) {
        return service.review(me, id, req.items());
    }

    @GetMapping("/admin/reimbursements")
    @Operation(summary = "Notes validées et remboursées", description = "Accès : ADMIN. Ajout au §5.15 : file de remboursement.")
    @ApiResponse(responseCode = "200", description = "Notes")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    public List<ClaimSummaryDto> toReimburse(@AuthenticationPrincipal AuthUser me) {
        return service.toReimburse(me);
    }

    @PostMapping("/reports/{id}/reimburse")
    @Operation(summary = "Marquer une note remboursée", description = "Accès : ADMIN. Note VALIDATED uniquement.")
    @ApiResponse(responseCode = "200", description = "Note remboursée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Note introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Note non validée (INVALID_STATUS_TRANSITION)")
    public ClaimDto reimburse(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.reimburse(me, id);
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques", description = "Accès : authentifié (ses dépenses ; ADMIN : toutes). Montants déclarés hors lignes refusées, par catégorie et par mois de l'année `year`.")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "401", description = U401)
    public StatsDto stats(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Integer year) {
        return service.stats(me, year);
    }
}
