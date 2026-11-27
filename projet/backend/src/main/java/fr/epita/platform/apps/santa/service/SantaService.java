package fr.epita.platform.apps.santa.service;

import fr.epita.platform.apps.santa.dto.SantaDtos.CandidateDto;
import fr.epita.platform.apps.santa.dto.SantaDtos.EventDto;
import fr.epita.platform.apps.santa.dto.SantaDtos.EventRequest;
import fr.epita.platform.apps.santa.dto.SantaDtos.EventSummaryDto;
import fr.epita.platform.apps.santa.dto.SantaDtos.ExclusionDto;
import fr.epita.platform.apps.santa.dto.SantaDtos.InvitationDto;
import fr.epita.platform.apps.santa.dto.SantaDtos.MyAssignmentDto;
import fr.epita.platform.apps.santa.dto.SantaDtos.MyGiftDto;
import fr.epita.platform.apps.santa.entity.Assignment;
import fr.epita.platform.apps.santa.entity.Exclusion;
import fr.epita.platform.apps.santa.entity.Invitation;
import fr.epita.platform.apps.santa.entity.InvitationStatus;
import fr.epita.platform.apps.santa.entity.SantaEvent;
import fr.epita.platform.apps.santa.repository.AssignmentRepository;
import fr.epita.platform.apps.santa.repository.ExclusionRepository;
import fr.epita.platform.apps.santa.repository.InvitationRepository;
import fr.epita.platform.apps.santa.repository.SantaEventRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SantaService {

    public static final int MIN_PARTICIPANTS = 3;

    private final SantaEventRepository events;
    private final InvitationRepository invitations;
    private final ExclusionRepository exclusions;
    private final AssignmentRepository assignments;
    private final UserRepository users;
    private final UserLookup userLookup;
    private final RandomGenerator random;
    private final Clock clock;

    // ---------- événements ----------

    @Transactional
    public EventDto create(AuthUser me, EventRequest req) {
        validateDates(req);
        SantaEvent event = new SantaEvent();
        event.setOrganizerId(me.id());
        apply(event, req);
        event = events.saveAndFlush(event);
        invitations.saveAndFlush(new Invitation(event.getId(), me.id(), InvitationStatus.ACCEPTED));
        return toDto(event, me);
    }

    @Transactional(readOnly = true)
    public List<EventSummaryDto> myEvents(AuthUser me) {
        Map<UUID, Invitation> mine = invitations.findByUserId(me.id()).stream()
                .collect(Collectors.toMap(Invitation::getEventId, i -> i));
        List<SantaEvent> organized = events.findAll().stream().filter(e -> e.getOrganizerId().equals(me.id())).toList();
        Set<UUID> ids = new java.util.HashSet<>(mine.keySet());
        organized.forEach(e -> ids.add(e.getId()));
        List<SantaEvent> list = events.findByIdInOrderByEventDateAsc(ids);
        Map<UUID, String> names = userLookup.namesOf(list, SantaEvent::getOrganizerId);
        return list.stream().map(e -> new EventSummaryDto(e.getId(), e.getName(), e.getEventDate(), e.getBudget(),
                e.getDrawDeadline(), names.get(e.getOrganizerId()), e.getOrganizerId().equals(me.id()), e.isDrawn(),
                mine.containsKey(e.getId()) ? mine.get(e.getId()).getStatus() : null,
                invitations.findByEventIdAndStatus(e.getId(), InvitationStatus.ACCEPTED).size())).toList();
    }

    @Transactional(readOnly = true)
    public EventDto get(AuthUser me, UUID id) {
        SantaEvent event = find(id);
        requireMember(me, event);
        return toDto(event, me);
    }

    @Transactional
    public EventDto update(AuthUser me, UUID id, EventRequest req) {
        SantaEvent event = lockOrganized(me, id);
        requireNotDrawn(event);
        validateDates(req);
        apply(event, req);
        return toDto(events.saveAndFlush(event), me);
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        SantaEvent event = find(id);
        requireOrganizer(me, event);
        events.delete(event);
    }

    // ---------- invités ----------

    @Transactional
    public EventDto invite(AuthUser me, UUID id, List<UUID> userIds) {
        SantaEvent event = lockOrganized(me, id);
        requireNotDrawn(event);
        for (UUID userId : userIds.stream().distinct().toList()) {
            if (!users.existsById(userId)) {
                throw ApiException.notFound("Utilisateur");
            }
            if (invitations.findByEventIdAndUserId(id, userId).isEmpty()) {
                invitations.save(new Invitation(id, userId, InvitationStatus.INVITED));
            }
        }
        invitations.flush();
        return toDto(event, me);
    }

    @Transactional
    public EventDto removeInvitation(AuthUser me, UUID id, UUID userId) {
        SantaEvent event = lockOrganized(me, id);
        requireNotDrawn(event);
        Invitation invitation = invitations.findByEventIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Invitation"));
        invitations.delete(invitation);
        exclusions.deleteInvolving(id, userId);
        return toDto(event, me);
    }

    @Transactional
    public EventDto respond(AuthUser me, UUID id, InvitationStatus status) {
        SantaEvent event = events.lockById(id).orElseThrow(() -> ApiException.notFound("Événement"));
        requireNotDrawn(event);
        if (status == InvitationStatus.INVITED) {
            throw ValidationException.field("status", "Réponse attendue : ACCEPTED ou DECLINED");
        }
        Invitation invitation = invitations.findByEventIdAndUserId(id, me.id())
                .orElseThrow(() -> ApiException.forbidden("Vous n'êtes pas invité à cet événement"));
        invitation.setStatus(status);
        return toDto(event, me);
    }

    @Transactional
    public EventDto setExclusions(AuthUser me, UUID id, List<ExclusionDto> list) {
        SantaEvent event = lockOrganized(me, id);
        requireNotDrawn(event);
        Set<UUID> invited = invitations.findByEventId(id).stream().map(Invitation::getUserId).collect(Collectors.toSet());
        for (ExclusionDto e : list) {
            if (e.giverId().equals(e.receiverId())) {
                throw ValidationException.field("exclusions", "Une exclusion relie deux personnes différentes");
            }
            if (!invited.contains(e.giverId()) || !invited.contains(e.receiverId())) {
                throw ValidationException.field("exclusions", "Les exclusions ne concernent que des invités");
            }
        }
        exclusions.deleteByEventId(id);
        exclusions.flush();
        list.stream().distinct().forEach(e -> exclusions.save(new Exclusion(id, e.giverId(), e.receiverId())));
        exclusions.flush();
        return toDto(event, me);
    }

    @Transactional
    public EventDto setWishlist(AuthUser me, UUID id, List<String> items) {
        SantaEvent event = find(id);
        Invitation invitation = invitations.findByEventIdAndUserId(id, me.id())
                .orElseThrow(() -> ApiException.forbidden("Vous n'êtes pas invité à cet événement"));
        List<String> clean = items.stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
        invitation.setWishlist(clean.isEmpty() ? null : String.join("\n", clean));
        return toDto(event, me);
    }

    // ---------- tirage ----------

    @Transactional
    public EventDto draw(AuthUser me, UUID id) {
        SantaEvent event = lockOrganized(me, id);
        requireNotDrawn(event);
        List<UUID> participants = invitations.findByEventIdAndStatus(id, InvitationStatus.ACCEPTED).stream()
                .map(Invitation::getUserId).toList();
        if (participants.size() < MIN_PARTICIPANTS) {
            throw ApiException.conflict("NOT_ENOUGH_PARTICIPANTS",
                    "Il faut au moins " + MIN_PARTICIPANTS + " participants ayant accepté");
        }
        Set<SantaDraw.Pair> excluded = exclusions.findByEventId(id).stream()
                .map(e -> new SantaDraw.Pair(e.getGiverId(), e.getReceiverId())).collect(Collectors.toSet());
        Map<UUID, UUID> result = SantaDraw.draw(participants, excluded, random)
                .orElseThrow(() -> ApiException.conflict("DRAW_IMPOSSIBLE",
                        "Aucun tirage ne respecte les exclusions : retirez-en ou invitez d'autres personnes"));
        result.forEach((giver, receiver) -> assignments.save(new Assignment(id, giver, receiver)));
        assignments.flush();
        event.setDrawnAt(clock.instant());
        return toDto(events.saveAndFlush(event), me);
    }

    @Transactional
    public EventDto cancelDraw(AuthUser me, UUID id) {
        SantaEvent event = lockOrganized(me, id);
        if (!event.isDrawn()) {
            throw ApiException.conflict("DRAW_NOT_DONE", "Le tirage n'a pas encore eu lieu");
        }
        if (event.getEventDate().isBefore(LocalDate.now(clock))) {
            throw ApiException.conflict("EVENT_PASSED", "L'événement est passé : le tirage ne peut plus être annulé");
        }
        assignments.deleteByEventId(id);
        event.setDrawnAt(null);
        return toDto(events.saveAndFlush(event), me);
    }

    /** Seule la personne qui offre voit son destinataire (et sa wishlist). */
    @Transactional(readOnly = true)
    public MyAssignmentDto myAssignment(AuthUser me, UUID id) {
        SantaEvent event = find(id);
        Assignment a = assignments.findByEventIdAndGiverId(id, me.id())
                .orElseThrow(() -> ApiException.notFound("Tirage"));
        List<String> wishlist = invitations.findByEventIdAndUserId(id, a.getReceiverId())
                .map(i -> split(i.getWishlist())).orElse(List.of());
        return new MyAssignmentDto(id, a.getReceiverId(), userLookup.name(a.getReceiverId()), wishlist, a.isReady(),
                event.getBudget(), event.getEventDate());
    }

    @Transactional
    public MyAssignmentDto markReady(AuthUser me, UUID id) {
        Assignment a = assignments.findByEventIdAndGiverId(id, me.id())
                .orElseThrow(() -> ApiException.notFound("Tirage"));
        if (!a.isReady()) {
            a.setReady(true);
            a.setReadyAt(clock.instant());
        }
        return myAssignment(me, id);
    }

    @Transactional(readOnly = true)
    public MyGiftDto myGift(AuthUser me, UUID id) {
        SantaEvent event = find(id);
        requireMember(me, event);
        boolean ready = assignments.findByEventIdAndReceiverId(id, me.id()).map(Assignment::isReady).orElse(false);
        return new MyGiftDto(id, event.isDrawn(), ready);
    }

    /** Utilisateurs actifs pouvant être invités (liste de choix pour l'organisateur). */
    @Transactional(readOnly = true)
    public List<CandidateDto> candidates() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream()
                .map(u -> new CandidateDto(u.getId(), u.getDisplayName())).toList();
    }

    // ---------- interne ----------

    private void validateDates(EventRequest req) {
        LocalDate today = LocalDate.now(clock);
        new ValidationException.Collector()
                .check(!req.eventDate().isBefore(today), "eventDate", "La date de l'événement doit être à venir")
                .check(!req.drawDeadline().isAfter(req.eventDate()), "drawDeadline",
                        "La date limite du tirage doit précéder l'événement")
                .throwIfAny();
    }

    private void apply(SantaEvent event, EventRequest req) {
        event.setName(req.name().trim());
        event.setEventDate(req.eventDate());
        event.setBudget(req.budget());
        event.setDrawDeadline(req.drawDeadline());
    }

    private SantaEvent find(UUID id) {
        return events.findById(id).orElseThrow(() -> ApiException.notFound("Événement"));
    }

    private SantaEvent lockOrganized(AuthUser me, UUID id) {
        SantaEvent event = events.lockById(id).orElseThrow(() -> ApiException.notFound("Événement"));
        requireOrganizer(me, event);
        return event;
    }

    private void requireOrganizer(AuthUser me, SantaEvent event) {
        if (!event.getOrganizerId().equals(me.id())) {
            throw ApiException.forbidden("Action réservée à l'organisateur");
        }
    }

    private void requireMember(AuthUser me, SantaEvent event) {
        if (!event.getOrganizerId().equals(me.id()) && invitations.findByEventIdAndUserId(event.getId(), me.id()).isEmpty()) {
            throw ApiException.forbidden("Vous n'êtes pas invité à cet événement");
        }
    }

    private void requireNotDrawn(SantaEvent event) {
        if (event.isDrawn()) {
            throw ApiException.conflict("DRAW_ALREADY_DONE", "Le tirage a eu lieu : la liste est figée");
        }
    }

    private static List<String> split(String wishlist) {
        return wishlist == null ? List.of() : Arrays.stream(wishlist.split("\n")).filter(s -> !s.isBlank()).toList();
    }

    private EventDto toDto(SantaEvent event, AuthUser me) {
        List<Invitation> list = invitations.findByEventId(event.getId());
        Map<UUID, String> names = userLookup.namesOf(list, Invitation::getUserId);
        boolean organizer = event.getOrganizerId().equals(me.id());
        Invitation mine = list.stream().filter(i -> i.getUserId().equals(me.id())).findFirst().orElse(null);
        List<InvitationDto> invited = list.stream()
                .map(i -> new InvitationDto(i.getUserId(), names.get(i.getUserId()), i.getStatus(), i.getWishlist() != null))
                .sorted((a, b) -> a.displayName().compareToIgnoreCase(b.displayName())).toList();
        List<ExclusionDto> excl = organizer
                ? exclusions.findByEventId(event.getId()).stream()
                .map(e -> new ExclusionDto(e.getGiverId(), e.getReceiverId())).toList()
                : null;
        return new EventDto(event.getId(), event.getName(), event.getEventDate(), event.getBudget(),
                event.getDrawDeadline(), event.getOrganizerId(), userLookup.name(event.getOrganizerId()), organizer,
                event.isDrawn(), event.getDrawnAt(), invited, excl, mine == null ? null : mine.getStatus(),
                mine == null ? List.of() : split(mine.getWishlist()));
    }
}
