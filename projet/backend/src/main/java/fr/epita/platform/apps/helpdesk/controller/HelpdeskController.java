package fr.epita.platform.apps.helpdesk.controller;

import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.AssignRequest;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.CommentRequest;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.ReopenRequest;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.StatsDto;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.StatusRequest;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.TicketDto;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.TicketRequest;
import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.Priority;
import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.TicketStatus;
import fr.epita.platform.apps.helpdesk.service.HelpdeskService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Helpdesk", description = "Tickets de support : ouverture, prise en charge par les techniciens, résolution.")
@RestController
@RequestMapping("/api/helpdesk")
@RequiredArgsConstructor
public class HelpdeskController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String NF = "Ticket introuvable ou non accessible (NOT_FOUND)";

    private final HelpdeskService service;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. TECHNICIAN si l'utilisateur est technicien (un ADMIN l'est implicitement).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @PostMapping("/tickets")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ouvrir un ticket", description = "Accès : authentifié. Délai de résolution attendu (`dueAt`) : 4 h (CRITICAL), 1 jour (HIGH), 3 jours (NORMAL), 5 jours ouvrés (LOW).")
    @ApiResponse(responseCode = "201", description = "Ticket ouvert (OPEN)")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    public TicketDto open(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody TicketRequest req) {
        return service.open(me, req);
    }

    @GetMapping("/tickets")
    @Operation(summary = "Lister les tickets", description = "Accès : authentifié. Demandeur : ses tickets ; TECHNICIAN : tous. Filtres `status`, `priority`, `assignee` (identifiant ou « me »), `overdue`. Un ticket résolu depuis plus de 7 jours apparaît fermé (CLOSED). Pagination : `page`, `size`.")
    @ApiResponse(responseCode = "200", description = "Page de tickets (sans les commentaires)")
    @ApiResponse(responseCode = "400", description = "Filtre invalide (VALIDATION_ERROR, BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public PageResponse<TicketDto> list(@AuthenticationPrincipal AuthUser me,
                                        @RequestParam(required = false) TicketStatus status,
                                        @RequestParam(required = false) Priority priority,
                                        @RequestParam(required = false) String assignee,
                                        @RequestParam(required = false) Boolean overdue,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return service.list(me, status, priority, assignee, overdue, page, size);
    }

    @GetMapping("/tickets/{id}")
    @Operation(summary = "Détail d'un ticket", description = "Accès : demandeur ou TECHNICIAN. Fil de discussion ; les commentaires internes ne sont renvoyés qu'aux techniciens.")
    @ApiResponse(responseCode = "200", description = "Ticket")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = NF)
    public TicketDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @PostMapping("/tickets/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Commenter un ticket", description = "Accès : demandeur ou TECHNICIAN. `internal=true` réservé aux techniciens. Pas de commentaire sur un ticket fermé.")
    @ApiResponse(responseCode = "201", description = "Ticket avec le fil mis à jour")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Commentaire interne réservé aux techniciens (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = NF)
    @ApiResponse(responseCode = "409", description = "Ticket fermé (TICKET_CLOSED)")
    public TicketDto comment(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody CommentRequest req) {
        return service.comment(me, id, req);
    }

    @PostMapping("/tickets/{id}/assign")
    @Operation(summary = "Assigner un ticket", description = "Accès : TECHNICIAN (à lui-même) ou ADMIN (à n'importe quel technicien).")
    @ApiResponse(responseCode = "200", description = "Ticket assigné")
    @ApiResponse(responseCode = "400", description = "La personne désignée n'est pas technicienne (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux techniciens, ou assignation à un autre (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Ticket ou technicien introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Ticket fermé (TICKET_CLOSED)")
    public TicketDto assign(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody AssignRequest req) {
        return service.assign(me, id, req.technicianId());
    }

    @PatchMapping("/tickets/{id}/status")
    @Operation(summary = "Changer le statut", description = "Accès : technicien assigné ou ADMIN. Transitions : OPEN → IN_PROGRESS → RESOLVED → CLOSED. Un commentaire public peut accompagner le changement.")
    @ApiResponse(responseCode = "200", description = "Ticket mis à jour")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le technicien assigné (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Ticket introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Transition interdite (INVALID_STATUS_TRANSITION)")
    public TicketDto status(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody StatusRequest req) {
        return service.changeStatus(me, id, req.status(), req.comment());
    }

    @PostMapping("/tickets/{id}/reopen")
    @Operation(summary = "Rouvrir un ticket résolu", description = "Accès : demandeur. Possible pendant 7 jours après la résolution ; le ticket repasse en IN_PROGRESS. Passé ce délai, il est fermé automatiquement.")
    @ApiResponse(responseCode = "200", description = "Ticket rouvert")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas le demandeur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Ticket introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "REOPEN_EXPIRED ou INVALID_STATUS_TRANSITION")
    public TicketDto reopen(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody ReopenRequest req) {
        return service.reopen(me, id, req.reason());
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques du support", description = "Accès : TECHNICIAN ou ADMIN. Tickets par statut, tickets ouverts par technicien, retards, temps moyen de résolution (heures).")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Réservé aux techniciens (FORBIDDEN)")
    public StatsDto stats(@AuthenticationPrincipal AuthUser me) {
        return service.stats(me);
    }
}
