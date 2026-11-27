package fr.epita.platform.apps.santa.controller;

import fr.epita.platform.apps.santa.dto.SantaDtos.CandidateDto;
import fr.epita.platform.apps.santa.dto.SantaDtos.EventDto;
import fr.epita.platform.apps.santa.dto.SantaDtos.EventRequest;
import fr.epita.platform.apps.santa.dto.SantaDtos.EventSummaryDto;
import fr.epita.platform.apps.santa.dto.SantaDtos.ExclusionsRequest;
import fr.epita.platform.apps.santa.dto.SantaDtos.InviteRequest;
import fr.epita.platform.apps.santa.dto.SantaDtos.MyAssignmentDto;
import fr.epita.platform.apps.santa.dto.SantaDtos.MyGiftDto;
import fr.epita.platform.apps.santa.dto.SantaDtos.RespondRequest;
import fr.epita.platform.apps.santa.dto.SantaDtos.WishlistRequest;
import fr.epita.platform.apps.santa.service.SantaService;
import fr.epita.platform.common.AuthUser;
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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Secret Santa", description = "Échange de cadeaux anonyme : invitations, exclusions, tirage au sort, wishlist.")
@RestController
@RequestMapping("/api/santa")
@RequiredArgsConstructor
public class SantaController {

    private final SantaService service;

    @Operation(summary = "Créer un événement", description = "Accès : authentifié. L'organisateur est invité (ACCEPTED) par défaut. `drawDeadline` ≤ `eventDate`, dates dans le futur.")
    @ApiResponse(responseCode = "201", description = "Événement créé")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody EventRequest req) {
        return service.create(me, req);
    }

    @Operation(summary = "Mes événements", description = "Accès : authentifié. Événements organisés ou auxquels on est invité.")
    @ApiResponse(responseCode = "200", description = "Événements")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/events")
    public List<EventSummaryDto> myEvents(@AuthenticationPrincipal AuthUser me) {
        return service.myEvents(me);
    }

    @Operation(summary = "Détail d'un événement", description = "Accès : organisateur ou invité. Invités et statut de réponse, état du tirage. Les attributions ne sont jamais exposées.")
    @ApiResponse(responseCode = "200", description = "Événement")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Ni organisateur ni invité (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable (NOT_FOUND)")
    @GetMapping("/events/{id}")
    public EventDto get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.get(me, id);
    }

    @Operation(summary = "Modifier un événement", description = "Accès : organisateur, avant le tirage.")
    @ApiResponse(responseCode = "200", description = "Événement modifié")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé à l'organisateur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Tirage déjà fait (DRAW_ALREADY_DONE)")
    @PutMapping("/events/{id}")
    public EventDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                           @Valid @RequestBody EventRequest req) {
        return service.update(me, id, req);
    }

    @Operation(summary = "Supprimer un événement", description = "Accès : organisateur.")
    @ApiResponse(responseCode = "204", description = "Événement supprimé")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé à l'organisateur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable (NOT_FOUND)")
    @DeleteMapping("/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        service.delete(me, id);
    }

    @Operation(summary = "Inviter des utilisateurs", description = "Accès : organisateur, avant le tirage. Les utilisateurs déjà invités sont ignorés.")
    @ApiResponse(responseCode = "200", description = "Événement mis à jour")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé à l'organisateur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Événement ou utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Tirage déjà fait (DRAW_ALREADY_DONE)")
    @PostMapping("/events/{id}/invitations")
    public EventDto invite(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                           @Valid @RequestBody InviteRequest req) {
        return service.invite(me, id, req.userIds());
    }

    @Operation(summary = "Retirer un invité", description = "Accès : organisateur, avant le tirage. L'organisateur peut se retirer lui-même.")
    @ApiResponse(responseCode = "200", description = "Événement mis à jour")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé à l'organisateur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Événement ou invitation introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Tirage déjà fait (DRAW_ALREADY_DONE)")
    @DeleteMapping("/events/{id}/invitations/{userId}")
    public EventDto removeInvitation(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                     @PathVariable UUID userId) {
        return service.removeInvitation(me, id, userId);
    }

    @Operation(summary = "Répondre à l'invitation", description = "Accès : invité, avant le tirage. ACCEPTED ou DECLINED ; seuls les ACCEPTED participent.")
    @ApiResponse(responseCode = "200", description = "Événement mis à jour")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Événement ou invitation introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Tirage déjà fait (DRAW_ALREADY_DONE)")
    @PutMapping("/events/{id}/invitations/me")
    public EventDto respond(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                            @Valid @RequestBody RespondRequest req) {
        return service.respond(me, id, req.status());
    }

    @Operation(summary = "Définir les exclusions", description = "Accès : organisateur, avant le tirage. Remplace la liste : chaque paire interdit à `giverId` d'offrir à `receiverId` ; les deux doivent être invités et différents.")
    @ApiResponse(responseCode = "200", description = "Événement mis à jour")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé à l'organisateur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Tirage déjà fait (DRAW_ALREADY_DONE)")
    @PutMapping("/events/{id}/exclusions")
    public EventDto exclusions(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                               @Valid @RequestBody ExclusionsRequest req) {
        return service.setExclusions(me, id, req.exclusions());
    }

    @Operation(summary = "Ma wishlist", description = "Accès : invité. Visible uniquement par la personne qui m'offre un cadeau.")
    @ApiResponse(responseCode = "200", description = "Événement mis à jour")
    @ApiResponse(responseCode = "400", description = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Pas invité à l'événement (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable (NOT_FOUND)")
    @PutMapping("/events/{id}/wishlist/me")
    public EventDto wishlist(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                             @Valid @RequestBody WishlistRequest req) {
        return service.setWishlist(me, id, req.items());
    }

    @Operation(summary = "Lancer le tirage", description = "Accès : organisateur. Au moins 3 participants ACCEPTED ; personne ne se tire soi-même ; exclusions respectées ; pas de paire réciproque au-delà de 3 participants. Les invités sont ensuite figés.")
    @ApiResponse(responseCode = "200", description = "Événement avec tirage fait")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé à l'organisateur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "NOT_ENOUGH_PARTICIPANTS, DRAW_IMPOSSIBLE ou DRAW_ALREADY_DONE")
    @PostMapping("/events/{id}/draw")
    public EventDto draw(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.draw(me, id);
    }

    @Operation(summary = "Annuler le tirage", description = "Accès : organisateur, tant que la date de l'événement n'est pas passée. Tous les participants seront réaffectés au prochain tirage.")
    @ApiResponse(responseCode = "200", description = "Événement sans tirage")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "403", description = "Réservé à l'organisateur (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "DRAW_NOT_DONE ou EVENT_PASSED")
    @DeleteMapping("/events/{id}/draw")
    public EventDto cancelDraw(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.cancelDraw(me, id);
    }

    @Operation(summary = "À qui j'offre", description = "Accès : participant, après le tirage. La personne à qui j'offre et sa wishlist ; jamais l'ensemble des attributions.")
    @ApiResponse(responseCode = "200", description = "Mon attribution")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable, ou pas d'attribution : tirage pas encore fait ou non participant (NOT_FOUND)")
    @GetMapping("/events/{id}/my-assignment")
    public MyAssignmentDto myAssignment(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.myAssignment(me, id);
    }

    @Operation(summary = "Marquer mon cadeau comme prêt", description = "Accès : participant, après le tirage.")
    @ApiResponse(responseCode = "200", description = "Mon attribution")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Événement introuvable, ou pas d'attribution : tirage pas encore fait ou non participant (NOT_FOUND)")
    @PostMapping("/events/{id}/my-assignment/ready")
    public MyAssignmentDto ready(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.markReady(me, id);
    }

    @Operation(summary = "Mon cadeau est-il prêt ?", description = "Accès : participant. Ne révèle jamais l'identité du Santa.")
    @ApiResponse(responseCode = "200", description = "État de mon cadeau")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @ApiResponse(responseCode = "404", description = "Événement ou participation introuvable (NOT_FOUND)")
    @GetMapping("/events/{id}/my-gift")
    public MyGiftDto myGift(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return service.myGift(me, id);
    }

    /** Ajout au §5.8 : liste des utilisateurs invitables (la gestion des utilisateurs est réservée aux ADMIN). */
    @Operation(summary = "Utilisateurs invitables", description = "Accès : authentifié. Utilisateurs actifs (identifiant et nom affiché) pour composer la liste d'invités.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = "Jeton absent, invalide ou expiré (UNAUTHORIZED)")
    @GetMapping("/candidates")
    public List<CandidateDto> candidates() {
        return service.candidates();
    }
}
