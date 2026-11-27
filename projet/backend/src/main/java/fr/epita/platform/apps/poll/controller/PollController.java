package fr.epita.platform.apps.poll.controller;

import fr.epita.platform.apps.poll.dto.PollDtos.PollDto;
import fr.epita.platform.apps.poll.dto.PollDtos.PollRequest;
import fr.epita.platform.apps.poll.dto.PollDtos.PollResultsDto;
import fr.epita.platform.apps.poll.dto.PollDtos.PollSummaryDto;
import fr.epita.platform.apps.poll.dto.PollDtos.VoteRequest;
import fr.epita.platform.apps.poll.service.PollService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Sondages", description = "Création de sondages et vote : un vote par utilisateur et par sondage.")
@RestController
@RequestMapping("/api/polls")
@RequiredArgsConstructor
public class PollController {

    private final PollService service;

    @Operation(summary = "Créer un sondage", description = "Accès : authentifié. Entre 2 et 10 options. `closesAt` facultatif, dans le futur.")
    @ApiResponse(responseCode = "201", description = "Sondage créé")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PollDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody PollRequest req) {
        return service.create(me, req);
    }

    @Operation(summary = "Lister les sondages", description = "Accès : authentifié. Filtres : `status` = open ou closed, `mine=true` pour ses propres sondages. Pagination : `page` (à partir de 0) et `size` ; réponse `{content, page, size, totalElements, totalPages}`.")
    @ApiResponse(responseCode = "200", description = "Page de sondages")
    @ApiResponse(responseCode = "400", description = "Statut inconnu (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping
    public PageResponse<PollSummaryDto> list(@AuthenticationPrincipal AuthUser me,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(defaultValue = "false") boolean mine,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.list(me, status, mine, page, size);
    }

    @Operation(summary = "Détail d'un sondage", description = "Accès : authentifié. Contient le vote de l'utilisateur connecté (`myVote`) s'il existe.")
    @ApiResponse(responseCode = "200", description = "Sondage")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Sondage introuvable (NOT_FOUND)")
    @GetMapping("/{id}")
    public PollDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @Operation(summary = "Modifier un sondage", description = "Accès : créateur ou ADMIN. Uniquement tant qu'aucun vote n'a été enregistré.")
    @ApiResponse(responseCode = "200", description = "Sondage modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Ni propriétaire ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Sondage introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Le sondage a déjà des votes (POLL_HAS_VOTES) ou est clos (POLL_CLOSED)")
    @PutMapping("/{id}")
    public PollDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                          @Valid @RequestBody PollRequest req) {
        return service.update(me, id, req);
    }

    @Operation(summary = "Clore un sondage", description = "Accès : créateur ou ADMIN. Plus aucun vote possible ensuite.")
    @ApiResponse(responseCode = "200", description = "Sondage clos")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Ni propriétaire ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Sondage introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Sondage déjà clos (POLL_CLOSED)")
    @PostMapping("/{id}/close")
    public PollDto close(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.close(me, id);
    }

    @Operation(summary = "Supprimer un sondage", description = "Accès : créateur ou ADMIN.")
    @ApiResponse(responseCode = "204", description = "Sondage supprimé")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Ni propriétaire ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Sondage introuvable (NOT_FOUND)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @Operation(summary = "Voter ou modifier son vote", description = "Accès : authentifié. Un vote par utilisateur, modifiable tant que le sondage est ouvert. Choix unique : exactement une option ; choix multiple : au moins une. Les options doivent appartenir au sondage.")
    @ApiResponse(responseCode = "200", description = "Sondage avec le vote enregistré")
    @ApiResponse(responseCode = "400", description = "Données invalides (VALIDATION_ERROR) ou options incohérentes (INVALID_VOTE)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Sondage introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Sondage clos (POLL_CLOSED)")
    @PutMapping("/{id}/vote")
    public PollDto vote(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                        @Valid @RequestBody VoteRequest req) {
        return service.vote(me, id, req.optionIds());
    }

    @Operation(summary = "Retirer son vote", description = "Accès : authentifié. Uniquement tant que le sondage est ouvert.")
    @ApiResponse(responseCode = "204", description = "Vote retiré")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Sondage ou vote introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Sondage clos (POLL_CLOSED)")
    @DeleteMapping("/{id}/vote")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeVote(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.removeVote(me, id);
    }

    @Operation(summary = "Résultats d'un sondage", description = "Accès : authentifié. Nombre de votes et pourcentage par option. Si les résultats ne sont visibles qu'après le vote, il faut avoir voté (ou être créateur, ADMIN, ou que le sondage soit clos).")
    @ApiResponse(responseCode = "200", description = "Résultats")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Résultats visibles seulement après avoir voté (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Sondage introuvable (NOT_FOUND)")
    @GetMapping("/{id}/results")
    public PollResultsDto results(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.results(me, id);
    }
}
