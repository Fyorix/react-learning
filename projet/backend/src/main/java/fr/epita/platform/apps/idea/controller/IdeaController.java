package fr.epita.platform.apps.idea.controller;

import fr.epita.platform.apps.idea.dto.IdeaDtos.CategoryDto;
import fr.epita.platform.apps.idea.dto.IdeaDtos.CommentDto;
import fr.epita.platform.apps.idea.dto.IdeaDtos.CommentRequest;
import fr.epita.platform.apps.idea.dto.IdeaDtos.IdeaDto;
import fr.epita.platform.apps.idea.dto.IdeaDtos.IdeaRequest;
import fr.epita.platform.apps.idea.dto.IdeaDtos.StatusRequest;
import fr.epita.platform.apps.idea.entity.IdeaCategory;
import fr.epita.platform.apps.idea.entity.IdeaStatus;
import fr.epita.platform.apps.idea.service.IdeaService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

@Tag(name = "Boîte à idées", description = "Soumission, vote et commentaires d'idées ; les ADMIN font avancer leur statut.")
@RestController
@RequestMapping("/api/ideas")
@RequiredArgsConstructor
public class IdeaController {

    private final IdeaService service;

    @Operation(summary = "Soumettre une idée", description = "Accès : authentifié. L'idée démarre au statut NEW.")
    @ApiResponse(responseCode = "201", description = "Idée créée")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdeaDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody IdeaRequest req) {
        return service.create(me, req);
    }

    @Operation(summary = "Lister les idées", description = "Accès : authentifié. Filtres `status`, `category`, `search` (titre ou description) ; `sort` = votes ou recent. Pagination : `page` (à partir de 0) et `size` ; réponse `{content, page, size, totalElements, totalPages}`.")
    @ApiResponse(responseCode = "200", description = "Page d'idées")
    @ApiResponse(responseCode = "400", description = "Filtre invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping
    public PageResponse<IdeaDto> list(@AuthenticationPrincipal AuthUser me,
                                      @RequestParam(required = false) IdeaStatus status,
                                      @RequestParam(required = false) IdeaCategory category,
                                      @RequestParam(defaultValue = "recent") String sort,
                                      @RequestParam(required = false) String search,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.list(me, status, category, sort, search, page, size);
    }

    @Operation(summary = "Catégories disponibles", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Catégories (code et libellé)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/categories")
    public List<CategoryDto> categories() {
        return service.categories();
    }

    @Operation(summary = "Détail d'une idée", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Idée")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Idée introuvable (NOT_FOUND)")
    @GetMapping("/{id}")
    public IdeaDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @Operation(summary = "Modifier son idée", description = "Accès : auteur uniquement, tant que l'idée est au statut NEW.")
    @ApiResponse(responseCode = "200", description = "Idée modifiée")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Seul l'auteur peut modifier (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Idée introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "L'idée n'est plus au statut NEW (IDEA_LOCKED)")
    @PutMapping("/{id}")
    public IdeaDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                          @Valid @RequestBody IdeaRequest req) {
        return service.update(me, id, req);
    }

    @Operation(summary = "Supprimer une idée", description = "Accès : auteur ou ADMIN.")
    @ApiResponse(responseCode = "204", description = "Idée supprimée")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Ni propriétaire ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Idée introuvable (NOT_FOUND)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @Operation(summary = "Voter pour une idée", description = "Accès : authentifié. Un vote par utilisateur et par idée ; pas de vote pour sa propre idée.")
    @ApiResponse(responseCode = "200", description = "Idée avec le nouveau total")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Idée introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà voté (ALREADY_VOTED) ou idée de l'utilisateur (OWN_IDEA_VOTE)")
    @PostMapping("/{id}/vote")
    public IdeaDto vote(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.vote(me, id);
    }

    @Operation(summary = "Retirer son vote", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Idée avec le nouveau total")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Idée ou vote introuvable (NOT_FOUND)")
    @DeleteMapping("/{id}/vote")
    public IdeaDto unvote(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.unvote(me, id);
    }

    @Operation(summary = "Changer le statut d'une idée", description = "Accès : ADMIN. Transitions : NEW → UNDER_REVIEW → ACCEPTED ou REJECTED ; ACCEPTED → DONE.")
    @ApiResponse(responseCode = "200", description = "Idée modifiée")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Idée introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Transition interdite (INVALID_STATUS_TRANSITION)")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public IdeaDto changeStatus(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                @Valid @RequestBody StatusRequest req) {
        return service.changeStatus(me, id, req);
    }

    @Operation(summary = "Commentaires d'une idée", description = "Accès : authentifié. Du plus ancien au plus récent.")
    @ApiResponse(responseCode = "200", description = "Commentaires")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Idée introuvable (NOT_FOUND)")
    @GetMapping("/{id}/comments")
    public List<CommentDto> comments(@PathVariable UUID id) {
        return service.comments(id);
    }

    @Operation(summary = "Commenter une idée", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "201", description = "Commentaire ajouté")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Idée introuvable (NOT_FOUND)")
    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto addComment(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                 @Valid @RequestBody CommentRequest req) {
        return service.addComment(me, id, req.content());
    }
}
