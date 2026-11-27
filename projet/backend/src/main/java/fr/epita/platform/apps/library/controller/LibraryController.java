package fr.epita.platform.apps.library.controller;

import fr.epita.platform.apps.library.dto.LibraryDtos.BookDto;
import fr.epita.platform.apps.library.dto.LibraryDtos.BookRequest;
import fr.epita.platform.apps.library.dto.LibraryDtos.LoanDto;
import fr.epita.platform.apps.library.service.LibraryService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Bibliothèque", description = "Prêt de livres partagés : emprunts, prolongations et file d'attente.")
@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService service;

    @Operation(summary = "Catalogue", description = "Accès : authentifié. Filtres `search` (titre ou auteur), `genre`, `available`. Pagination : `page` (à partir de 0) et `size` ; réponse `{content, page, size, totalElements, totalPages}`.")
    @ApiResponse(responseCode = "200", description = "Page de livres")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/books")
    public PageResponse<BookDto> list(@AuthenticationPrincipal AuthUser me,
                                      @RequestParam(required = false) String search,
                                      @RequestParam(required = false) String genre,
                                      @RequestParam(required = false) Boolean available,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.list(me, search, genre, available, page, size);
    }

    @Operation(summary = "Détail d'un livre", description = "Accès : authentifié. Disponibilité, taille de la file, position de l'utilisateur dans la file.")
    @ApiResponse(responseCode = "200", description = "Livre")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Livre introuvable (NOT_FOUND)")
    @GetMapping("/books/{id}")
    public BookDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @Operation(summary = "Ajouter un livre", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "201", description = "Livre ajouté")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @PostMapping("/books")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public BookDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody BookRequest req) {
        return service.create(me, req);
    }

    @Operation(summary = "Modifier un livre", description = "Accès : ADMIN.")
    @ApiResponse(responseCode = "200", description = "Livre modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Livre introuvable (NOT_FOUND)")
    @PutMapping("/books/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public BookDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                          @Valid @RequestBody BookRequest req) {
        return service.update(me, id, req);
    }

    @Operation(summary = "Supprimer un livre", description = "Accès : ADMIN. Impossible tant qu'un emprunt est actif.")
    @ApiResponse(responseCode = "204", description = "Livre supprimé")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Livre introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Emprunt actif (BOOK_HAS_ACTIVE_LOAN)")
    @DeleteMapping("/books/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @Operation(summary = "Emprunter un livre", description = "Accès : authentifié. Durée 14 jours. 3 emprunts en cours maximum ; impossible avec un emprunt en retard. Un livre rendu avec une file d'attente est réservé 3 jours au premier de la file.")
    @ApiResponse(responseCode = "201", description = "Emprunt créé")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Livre introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "LOAN_LIMIT_REACHED, BOOK_UNAVAILABLE, OVERDUE_LOANS ou BOOK_HAS_ACTIVE_LOAN")
    @PostMapping("/books/{id}/loans")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanDto borrow(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.borrow(me, id);
    }

    @Operation(summary = "Mes emprunts", description = "Accès : authentifié. Emprunts en cours et passés.")
    @ApiResponse(responseCode = "200", description = "Emprunts")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/loans/me")
    public List<LoanDto> myLoans(@AuthenticationPrincipal AuthUser me) {
        return service.myLoans(me);
    }

    @Operation(summary = "Rendre un livre", description = "Accès : emprunteur ou ADMIN. Si la file n'est pas vide, le livre est réservé 3 jours au premier de la file.")
    @ApiResponse(responseCode = "200", description = "Emprunt clôturé")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Ni propriétaire ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Emprunt introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà rendu (LOAN_ALREADY_RETURNED)")
    @PostMapping("/loans/{id}/return")
    public LoanDto returnLoan(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.returnLoan(me, id);
    }

    @Operation(summary = "Prolonger un emprunt", description = "Accès : emprunteur. Une seule prolongation de 7 jours, refusée si la file d'attente du livre n'est pas vide.")
    @ApiResponse(responseCode = "200", description = "Emprunt prolongé")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Seul l'emprunteur peut prolonger (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Emprunt introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Prolongation impossible (EXTENSION_NOT_ALLOWED)")
    @PostMapping("/loans/{id}/extend")
    public LoanDto extend(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.extend(me, id);
    }

    @Operation(summary = "Rejoindre la file d'attente", description = "Accès : authentifié. Uniquement sur un livre indisponible, que l'on n'a pas emprunté, une seule fois.")
    @ApiResponse(responseCode = "200", description = "Livre avec ma position")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Livre introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "BOOK_AVAILABLE, ALREADY_BORROWED ou ALREADY_IN_QUEUE")
    @PostMapping("/books/{id}/queue")
    public BookDto joinQueue(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.joinQueue(me, id);
    }

    @Operation(summary = "Quitter la file d'attente", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Livre mis à jour")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Livre ou place dans la file introuvable (NOT_FOUND)")
    @DeleteMapping("/books/{id}/queue")
    public BookDto leaveQueue(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.leaveQueue(me, id);
    }

    @Operation(summary = "Suivi des emprunts", description = "Accès : ADMIN. `overdue=true` : uniquement les retards. Pagination : `page` (à partir de 0) et `size` ; réponse `{content, page, size, totalElements, totalPages}`.")
    @ApiResponse(responseCode = "200", description = "Page d'emprunts")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @GetMapping("/admin/loans")
    public PageResponse<LoanDto> adminLoans(@RequestParam(defaultValue = "false") boolean overdue,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return service.adminLoans(overdue, page, size);
    }
}
