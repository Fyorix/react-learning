package fr.epita.platform.apps.kanban.controller;

import fr.epita.platform.apps.kanban.dto.KanbanDtos.BoardDto;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.BoardRequest;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.BoardSummaryDto;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.CardDto;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.CardRequest;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.ColumnRequest;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.MoveDto;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.MoveRequest;
import fr.epita.platform.apps.kanban.dto.KanbanDtos.MyCardDto;
import fr.epita.platform.apps.kanban.service.KanbanService;
import fr.epita.platform.common.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Kanban", description = "Tableaux de tâches à colonnes, cartes déplacées par glisser-déposer.")
@RestController
@RequestMapping("/api/kanban")
@RequiredArgsConstructor
public class KanbanController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String MEMBER = "Réservé aux membres du tableau (FORBIDDEN)";
    private static final String OWNER = "Réservé au propriétaire du tableau (FORBIDDEN)";

    private final KanbanService service;

    @GetMapping("/boards")
    @Operation(summary = "Mes tableaux", description = "Accès : authentifié. Tableaux dont l'utilisateur est propriétaire ou membre.")
    @ApiResponse(responseCode = "200", description = "Tableaux")
    @ApiResponse(responseCode = "401", description = U401)
    public List<BoardSummaryDto> boards(@AuthenticationPrincipal AuthUser me) {
        return service.myBoards(me);
    }

    @PostMapping("/boards")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un tableau", description = "Accès : authentifié (devient propriétaire). Colonnes par défaut : À faire, En cours, Terminé.")
    @ApiResponse(responseCode = "201", description = "Tableau créé")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Membre introuvable (NOT_FOUND)")
    public BoardDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody BoardRequest req) {
        return service.create(me, req);
    }

    @GetMapping("/boards/{id}")
    @Operation(summary = "Tableau complet", description = "Accès : membres. Colonnes et cartes ordonnées ; `overdue` = échéance passée hors dernière colonne.")
    @ApiResponse(responseCode = "200", description = "Tableau")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MEMBER)
    @ApiResponse(responseCode = "404", description = "Tableau introuvable (NOT_FOUND)")
    public BoardDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PutMapping("/boards/{id}")
    @Operation(summary = "Modifier un tableau", description = "Accès : propriétaire. Renommer, changer les membres ; un membre retiré n'est plus assigné aux cartes.")
    @ApiResponse(responseCode = "200", description = "Tableau modifié")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWNER)
    @ApiResponse(responseCode = "404", description = "Tableau ou membre introuvable (NOT_FOUND)")
    public BoardDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody BoardRequest req) {
        return service.update(me, id, req);
    }

    @DeleteMapping("/boards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un tableau", description = "Accès : propriétaire. Supprime colonnes et cartes.")
    @ApiResponse(responseCode = "204", description = "Tableau supprimé")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = OWNER)
    @ApiResponse(responseCode = "404", description = "Tableau introuvable (NOT_FOUND)")
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @PostMapping("/boards/{id}/columns")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une colonne", description = "Accès : membres. `position` facultative (à la fin par défaut).")
    @ApiResponse(responseCode = "201", description = "Tableau mis à jour")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MEMBER)
    @ApiResponse(responseCode = "404", description = "Tableau introuvable (NOT_FOUND)")
    public BoardDto addColumn(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ColumnRequest req) {
        return service.addColumn(me, id, req);
    }

    @PutMapping("/columns/{id}")
    @Operation(summary = "Modifier une colonne", description = "Accès : membres. Renommer, limite WIP (jamais sous le nombre de cartes présentes), position.")
    @ApiResponse(responseCode = "200", description = "Tableau mis à jour")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MEMBER)
    @ApiResponse(responseCode = "404", description = "Colonne introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Limite inférieure au nombre de cartes (WIP_LIMIT_REACHED)")
    public BoardDto updateColumn(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ColumnRequest req) {
        return service.updateColumn(me, id, req);
    }

    @DeleteMapping("/columns/{id}")
    @Operation(summary = "Supprimer une colonne", description = "Accès : membres. La colonne doit être vide.")
    @ApiResponse(responseCode = "200", description = "Tableau mis à jour")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MEMBER)
    @ApiResponse(responseCode = "404", description = "Colonne introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Colonne non vide (COLUMN_NOT_EMPTY)")
    public BoardDto deleteColumn(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.deleteColumn(me, id);
    }

    @PostMapping("/columns/{id}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une carte", description = "Accès : membres. Ajoutée en bas de la colonne ; refusée si la colonne a atteint sa limite WIP. L'assigné doit être membre.")
    @ApiResponse(responseCode = "201", description = "Carte créée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MEMBER)
    @ApiResponse(responseCode = "404", description = "Colonne introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Colonne pleine (WIP_LIMIT_REACHED)")
    public CardDto addCard(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody CardRequest req) {
        return service.addCard(me, id, req);
    }

    @PutMapping("/cards/{id}")
    @Operation(summary = "Modifier une carte", description = "Accès : membres.")
    @ApiResponse(responseCode = "200", description = "Carte modifiée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MEMBER)
    @ApiResponse(responseCode = "404", description = "Carte introuvable (NOT_FOUND)")
    public CardDto updateCard(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody CardRequest req) {
        return service.updateCard(me, id, req);
    }

    @DeleteMapping("/cards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer une carte", description = "Accès : membres.")
    @ApiResponse(responseCode = "204", description = "Carte supprimée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MEMBER)
    @ApiResponse(responseCode = "404", description = "Carte introuvable (NOT_FOUND)")
    public void deleteCard(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.deleteCard(me, id);
    }

    @PostMapping("/cards/{id}/move")
    @Operation(summary = "Déplacer une carte", description = "Accès : membres. Glisser-déposer : colonne et position d'arrivée (même tableau). Refusé si la colonne cible est pleine ; les positions des deux colonnes sont recalculées dans la même transaction. Renvoie le tableau complet pour resynchroniser le front.")
    @ApiResponse(responseCode = "200", description = "Tableau après déplacement")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MEMBER)
    @ApiResponse(responseCode = "404", description = "Carte ou colonne introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Colonne pleine (WIP_LIMIT_REACHED)")
    public BoardDto move(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody MoveRequest req) {
        return service.move(me, id, req.columnId(), req.position());
    }

    @GetMapping("/cards/{id}/history")
    @Operation(summary = "Historique d'une carte", description = "Accès : membres. Création et changements de colonne, avec auteur et date.")
    @ApiResponse(responseCode = "200", description = "Déplacements")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = MEMBER)
    @ApiResponse(responseCode = "404", description = "Carte introuvable (NOT_FOUND)")
    public List<MoveDto> history(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.history(me, id);
    }

    @GetMapping("/candidates")
    @Operation(summary = "Membres possibles", description = "Accès : authentifié. Utilisateurs actifs (identifiant, nom affiché) pour composer un tableau (ajout au §5.17).")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<fr.epita.platform.user.dto.UserDtos.UserSummary> candidates() {
        return service.candidates();
    }

    @GetMapping("/me/cards")
    @Operation(summary = "Mes cartes", description = "Accès : authentifié. Cartes qui me sont assignées, tous tableaux confondus.")
    @ApiResponse(responseCode = "200", description = "Cartes")
    @ApiResponse(responseCode = "401", description = U401)
    public List<MyCardDto> myCards(@AuthenticationPrincipal AuthUser me) {
        return service.myCards(me);
    }
}
