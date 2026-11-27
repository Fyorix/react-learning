package fr.epita.platform.apps.events.controller;

import fr.epita.platform.apps.events.dto.EventDtos.EventDto;
import fr.epita.platform.apps.events.dto.EventDtos.EventRequest;
import fr.epita.platform.apps.events.dto.EventDtos.RegistrationResultDto;
import fr.epita.platform.apps.events.service.EventService;
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

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Événements", description = "Événements et afterworks : inscriptions avec capacité et liste d'attente.")
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService service;

    @Operation(summary = "Créer un événement", description = "Accès : authentifié (l'utilisateur devient organisateur, inscrit d'office). Début dans le futur ; `registrationDeadline` ≤ `startsAt` ; capacité de 1 à 500.")
    @ApiResponse(responseCode = "201", description = "Événement créé")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody EventRequest req) {
        return service.create(me, req);
    }

    @Operation(summary = "Lister les événements", description = "Accès : authentifié. `period` = upcoming ou past ; `mine=true` pour ses événements organisés. Pagination : `page` (à partir de 0) et `size` ; réponse `{content, page, size, totalElements, totalPages}`.")
    @ApiResponse(responseCode = "200", description = "Page d'événements")
    @ApiResponse(responseCode = "400", description = "Période inconnue (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping
    public PageResponse<EventDto> list(@AuthenticationPrincipal AuthUser me,
                                       @RequestParam(defaultValue = "upcoming") String period,
                                       @RequestParam(defaultValue = "false") boolean mine,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return service.list(me, period, mine, page, size);
    }

    @Operation(summary = "Mes inscriptions", description = "Accès : authentifié. Avec mon statut (CONFIRMED ou WAITLISTED et position).")
    @ApiResponse(responseCode = "200", description = "Événements")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/me/registrations")
    public List<EventDto> myRegistrations(@AuthenticationPrincipal AuthUser me) {
        return service.myRegistrations(me);
    }

    @Operation(summary = "Détail d'un événement", description = "Accès : authentifié. Places restantes, statut de l'utilisateur ; la liste des inscrits n'est renvoyée qu'à l'organisateur et aux ADMIN.")
    @ApiResponse(responseCode = "200", description = "Événement")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable (NOT_FOUND)")
    @GetMapping("/{id}")
    public EventDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @Operation(summary = "Modifier un événement", description = "Accès : organisateur ou ADMIN. La capacité peut augmenter (les premiers en attente sont confirmés) mais jamais descendre sous le nombre de CONFIRMED.")
    @ApiResponse(responseCode = "200", description = "Événement modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Ni organisateur ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "CAPACITY_BELOW_CONFIRMED ou EVENT_CANCELLED")
    @PutMapping("/{id}")
    public EventDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                           @Valid @RequestBody EventRequest req) {
        return service.update(me, id, req);
    }

    @Operation(summary = "Annuler un événement", description = "Accès : organisateur ou ADMIN. L'événement passe en CANCELLED et refuse toute inscription.")
    @ApiResponse(responseCode = "200", description = "Événement annulé")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Ni organisateur ni ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà annulé (EVENT_CANCELLED)")
    @PostMapping("/{id}/cancel")
    public EventDto cancel(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.cancel(me, id);
    }

    @Operation(summary = "S'inscrire", description = "Accès : authentifié. Une inscription par utilisateur ; l'organisateur est inscrit d'office. Si l'événement est complet : WAITLISTED avec une position.")
    @ApiResponse(responseCode = "201", description = "Inscription : CONFIRMED ou WAITLISTED (+ position)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "REGISTRATION_CLOSED, ALREADY_REGISTERED, ORGANIZER_ALREADY_IN ou EVENT_CANCELLED")
    @PostMapping("/{id}/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResultDto register(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.register(me, id);
    }

    @Operation(summary = "Se désinscrire", description = "Accès : inscrit, avant la date limite. Si un CONFIRMED se désinscrit, le premier WAITLISTED est confirmé dans la même transaction.")
    @ApiResponse(responseCode = "200", description = "Événement mis à jour")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Événement ou inscription introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Inscriptions closes (REGISTRATION_CLOSED)")
    @DeleteMapping("/{id}/registrations/me")
    public EventDto unregister(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.unregister(me, id);
    }
}
