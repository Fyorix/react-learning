package fr.epita.platform.apps.association.controller;

import fr.epita.platform.apps.association.dto.AssociationDtos.*;
import fr.epita.platform.apps.association.service.AssociationService;
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

@Tag(name = "Association", description = "Adhérents, cotisations annuelles, activités avec liste d'attente, assemblée générale à vote secret.")
@RestController
@RequestMapping("/api/association")
@RequiredArgsConstructor
public class AssociationController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String BRD = "Réservé au bureau, rôle BOARD (FORBIDDEN)";
    private static final String A404 = "Activité introuvable (NOT_FOUND)";
    private static final String G404 = "Assemblée introuvable (NOT_FOUND)";

    private final AssociationService service;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. BOARD le cas échéant (un ADMIN l'a d'office).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @GetMapping("/me")
    @Operation(summary = "Mon adhésion", description = "Accès : authentifié. Saison en cours, statut à jour, cotisations.")
    @ApiResponse(responseCode = "200", description = "Adhésion")
    @ApiResponse(responseCode = "401", description = U401)
    public MyMembershipDto me(@AuthenticationPrincipal AuthUser me) {
        return service.me(me);
    }

    @GetMapping("/members")
    @Operation(summary = "Adhérents", description = "Accès : BOARD. Filtres : à jour (`upToDate`) et recherche nom / e-mail.")
    @ApiResponse(responseCode = "200", description = "Adhérents")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = BRD)
    public List<MemberDto> members(@AuthenticationPrincipal AuthUser me, @RequestParam(required = false) Boolean upToDate,
                                   @RequestParam(required = false) String search) {
        return service.members(me, upToDate, search);
    }

    @PostMapping("/members/{userId}/fees")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistrer une cotisation", description = "Accès : BOARD. Une par adhérent et par saison.")
    @ApiResponse(responseCode = "201", description = "Cotisation")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = BRD)
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà payée pour la saison (ALREADY_PAID)")
    public FeeDto recordFee(@AuthenticationPrincipal AuthUser me, @PathVariable UUID userId, @Valid @RequestBody FeeRequest req) {
        return service.recordFee(me, userId, req);
    }

    @GetMapping("/fees/reminders")
    @Operation(summary = "Cotisations à relancer", description = "Accès : BOARD. Adhérents de la saison précédente pas encore à jour.")
    @ApiResponse(responseCode = "200", description = "Adhérents")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = BRD)
    public List<MemberDto> reminders(@AuthenticationPrincipal AuthUser me) {
        return service.reminders(me);
    }

    @GetMapping("/activities")
    @Operation(summary = "Activités", description = "Accès : authentifié. Avec mon inscription et ma position en liste d'attente.")
    @ApiResponse(responseCode = "200", description = "Activités")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ActivityDto> activities(@AuthenticationPrincipal AuthUser me) {
        return service.activities(me);
    }

    @PostMapping("/activities")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer une activité", description = "Accès : BOARD.")
    @ApiResponse(responseCode = "201", description = "Activité")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = BRD)
    public ActivityDto createActivity(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody ActivityRequest req) {
        return service.createActivity(me, req);
    }

    @PostMapping("/activities/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "S'inscrire", description = "Accès : authentifié (adhérent à jour si l'activité est réservée). Au-delà de la capacité : liste d'attente.")
    @ApiResponse(responseCode = "201", description = "Activité")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = A404)
    @ApiResponse(responseCode = "409", description = "MEMBERSHIP_EXPIRED, ALREADY_REGISTERED ou REGISTRATION_CLOSED")
    public ActivityDto register(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.register(me, id);
    }

    @DeleteMapping("/activities/{id}/registrations/me")
    @Operation(summary = "Se désinscrire", description = "Accès : inscrit. Le premier en attente est confirmé.")
    @ApiResponse(responseCode = "200", description = "Activité")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Activité ou inscription introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Activité passée (REGISTRATION_CLOSED)")
    public ActivityDto unregister(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.unregister(me, id);
    }

    @GetMapping("/assemblies")
    @Operation(summary = "Assemblées générales", description = "Accès : authentifié.")
    @ApiResponse(responseCode = "200", description = "Assemblées")
    @ApiResponse(responseCode = "401", description = U401)
    public List<AssemblyDto> assemblies(@AuthenticationPrincipal AuthUser me) {
        return service.assemblies(me);
    }

    @PostMapping("/assemblies")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Convoquer une AG", description = "Accès : BOARD. Quorum en % des adhérents à jour, 1 à 20 résolutions.")
    @ApiResponse(responseCode = "201", description = "Assemblée CONVENED")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = BRD)
    public AssemblyDto convene(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody AssemblyRequest req) {
        return service.convene(me, req);
    }

    @GetMapping("/assemblies/{id}")
    @Operation(summary = "Assemblée", description = "Accès : authentifié. Résolutions, participation ; décomptes et résultats après clôture.")
    @ApiResponse(responseCode = "200", description = "Assemblée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = G404)
    public AssemblyDto assembly(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.assembly(me, id);
    }

    @PostMapping("/assemblies/{id}/open")
    @Operation(summary = "Ouvrir le vote", description = "Accès : BOARD.")
    @ApiResponse(responseCode = "200", description = "Assemblée OPEN")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = BRD)
    @ApiResponse(responseCode = "404", description = G404)
    @ApiResponse(responseCode = "409", description = "Déjà ouverte (INVALID_STATUS_TRANSITION)")
    public AssemblyDto open(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.open(me, id);
    }

    @PutMapping("/assemblies/{id}/votes/me")
    @Operation(summary = "Voter", description = "Accès : adhérent à jour, vote ouvert. Une voix par résolution, définitive et secrète.")
    @ApiResponse(responseCode = "200", description = "Assemblée")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = G404)
    @ApiResponse(responseCode = "409", description = "ALREADY_VOTED, MEMBERSHIP_EXPIRED ou VOTE_NOT_OPEN")
    public AssemblyDto vote(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody VotesRequest req) {
        return service.vote(me, id, req.votes());
    }

    @PostMapping("/assemblies/{id}/close")
    @Operation(summary = "Clore le vote", description = "Accès : BOARD. Quorum : votants ≥ quorum % des adhérents à jour ; adoptée si quorum et POUR > CONTRE.")
    @ApiResponse(responseCode = "200", description = "Assemblée CLOSED avec résultats")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = BRD)
    @ApiResponse(responseCode = "404", description = G404)
    @ApiResponse(responseCode = "409", description = "Vote non ouvert (INVALID_STATUS_TRANSITION)")
    public AssemblyDto close(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.close(me, id);
    }
}
