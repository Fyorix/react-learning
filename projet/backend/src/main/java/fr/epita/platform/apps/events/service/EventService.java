package fr.epita.platform.apps.events.service;

import fr.epita.platform.apps.events.dto.EventDtos.AttendeeDto;
import fr.epita.platform.apps.events.dto.EventDtos.EventDto;
import fr.epita.platform.apps.events.dto.EventDtos.EventRequest;
import fr.epita.platform.apps.events.dto.EventDtos.RegistrationResultDto;
import fr.epita.platform.apps.events.entity.Event;
import fr.epita.platform.apps.events.entity.EventStatus;
import fr.epita.platform.apps.events.entity.Registration;
import fr.epita.platform.apps.events.entity.RegistrationStatus;
import fr.epita.platform.apps.events.repository.EventRepository;
import fr.epita.platform.apps.events.repository.RegistrationRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final UserLookup userLookup;
    private final Clock clock;

    @Transactional
    public EventDto create(AuthUser me, EventRequest req) {
        validate(req, true);
        Event event = new Event();
        event.setOrganizerId(me.id());
        apply(event, req);
        return toDto(events.saveAndFlush(event), me);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventDto> list(AuthUser me, String period, boolean mine, int page, int size) {
        Instant now = clock.instant();
        Set<UUID> myEvents = registrations.findByUserId(me.id()).stream().map(Registration::getEventId)
                .collect(Collectors.toSet());
        List<Event> list = new ArrayList<>(events.findAllByOrderByStartsAtAsc().stream()
                .filter(e -> "past".equals(period) ? !e.getStartsAt().isAfter(now) : e.getStartsAt().isAfter(now))
                .filter(e -> !mine || e.getOrganizerId().equals(me.id()) || myEvents.contains(e.getId()))
                .toList());
        if ("past".equals(period)) {
            list.sort(Comparator.comparing(Event::getStartsAt).reversed());
        }
        return PageResponse.ofList(toDtos(list, me, false), page, size);
    }

    @Transactional(readOnly = true)
    public EventDto get(AuthUser me, UUID id) {
        return toDto(find(id), me);
    }

    @Transactional
    public EventDto update(AuthUser me, UUID id, EventRequest req) {
        Event event = events.lockById(id).orElseThrow(() -> ApiException.notFound("Événement"));
        me.requireOwnerOrAdmin(event.getOrganizerId());
        requireActive(event);
        validate(req, false);
        long confirmed = registrations.countByEventIdAndStatus(id, RegistrationStatus.CONFIRMED);
        if (req.capacity() < confirmed) {
            throw ApiException.conflict("CAPACITY_BELOW_CONFIRMED",
                    confirmed + " personne(s) déjà confirmée(s) : la capacité ne peut pas descendre en dessous");
        }
        apply(event, req);
        events.saveAndFlush(event);
        promoteWaitlist(event);
        return toDto(event, me);
    }

    @Transactional
    public EventDto cancel(AuthUser me, UUID id) {
        Event event = find(id);
        me.requireOwnerOrAdmin(event.getOrganizerId());
        event.setStatus(EventStatus.CANCELLED);
        return toDto(events.saveAndFlush(event), me);
    }

    /** Complet → liste d'attente. L'événement est verrouillé pour sérialiser les inscriptions. */
    @Transactional
    public RegistrationResultDto register(AuthUser me, UUID id) {
        Event event = events.lockById(id).orElseThrow(() -> ApiException.notFound("Événement"));
        requireActive(event);
        requireRegistrationOpen(event);
        if (event.getOrganizerId().equals(me.id())) {
            throw ApiException.conflict("ORGANIZER_ALREADY_IN", "L'organisateur participe d'office à son événement");
        }
        if (registrations.findByEventIdAndUserId(id, me.id()).isPresent()) {
            throw ApiException.conflict("ALREADY_REGISTERED", "Vous êtes déjà inscrit");
        }
        long confirmed = registrations.countByEventIdAndStatus(id, RegistrationStatus.CONFIRMED);
        Registration r = new Registration();
        r.setEventId(id);
        r.setUserId(me.id());
        r.setCreatedAt(clock.instant());
        r.setStatus(confirmed < event.getCapacity() ? RegistrationStatus.CONFIRMED : RegistrationStatus.WAITLISTED);
        registrations.saveAndFlush(r);
        return new RegistrationResultDto(r.getStatus(), position(id, me.id()));
    }

    /** Une place confirmée libérée passe au premier de la liste d'attente, dans la même transaction. */
    @Transactional
    public EventDto unregister(AuthUser me, UUID id) {
        Event event = events.lockById(id).orElseThrow(() -> ApiException.notFound("Événement"));
        requireRegistrationOpen(event);
        Registration r = registrations.findByEventIdAndUserId(id, me.id())
                .orElseThrow(() -> ApiException.notFound("Inscription"));
        registrations.delete(r);
        registrations.flush();
        promoteWaitlist(event);
        return toDto(event, me);
    }

    @Transactional(readOnly = true)
    public List<EventDto> myRegistrations(AuthUser me) {
        Set<UUID> ids = registrations.findByUserId(me.id()).stream().map(Registration::getEventId)
                .collect(Collectors.toSet());
        List<Event> list = events.findAllById(ids).stream().sorted(Comparator.comparing(Event::getStartsAt)).toList();
        return toDtos(list, me, false);
    }

    private void promoteWaitlist(Event event) {
        if (event.getStatus() != EventStatus.ACTIVE) {
            return;
        }
        long confirmed = registrations.countByEventIdAndStatus(event.getId(), RegistrationStatus.CONFIRMED);
        for (Registration w : registrations.findByEventIdAndStatusOrderByCreatedAtAsc(event.getId(),
                RegistrationStatus.WAITLISTED)) {
            if (confirmed >= event.getCapacity()) {
                break;
            }
            w.setStatus(RegistrationStatus.CONFIRMED);
            confirmed++;
        }
        registrations.flush();
    }

    private Integer position(UUID eventId, UUID userId) {
        List<Registration> waiting = registrations.findByEventIdAndStatusOrderByCreatedAtAsc(eventId,
                RegistrationStatus.WAITLISTED);
        for (int i = 0; i < waiting.size(); i++) {
            if (waiting.get(i).getUserId().equals(userId)) {
                return i + 1;
            }
        }
        return null;
    }

    private void validate(EventRequest req, boolean creation) {
        Instant now = clock.instant();
        new ValidationException.Collector()
                .check(!creation || req.startsAt().isAfter(now), "startsAt", "L'événement doit être à venir")
                .check(!req.registrationDeadline().isAfter(req.startsAt()), "registrationDeadline",
                        "La date limite d'inscription doit précéder l'événement")
                .throwIfAny();
    }

    private void requireActive(Event event) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw ApiException.conflict("EVENT_CANCELLED", "L'événement est annulé");
        }
    }

    private void requireRegistrationOpen(Event event) {
        if (clock.instant().isAfter(event.getRegistrationDeadline())) {
            throw ApiException.conflict("REGISTRATION_CLOSED", "Les inscriptions sont closes");
        }
    }

    private void apply(Event event, EventRequest req) {
        event.setTitle(req.title().trim());
        event.setDescription(req.description().trim());
        event.setLocation(req.location().trim());
        event.setStartsAt(req.startsAt());
        event.setCapacity(req.capacity());
        event.setRegistrationDeadline(req.registrationDeadline());
    }

    private Event find(UUID id) {
        return events.findById(id).orElseThrow(() -> ApiException.notFound("Événement"));
    }

    private EventDto toDto(Event event, AuthUser me) {
        return toDtos(List.of(event), me, true).getFirst();
    }

    private List<EventDto> toDtos(List<Event> list, AuthUser me, boolean withAttendees) {
        Map<UUID, List<Registration>> byEvent = registrations.findByEventIdIn(list.stream().map(Event::getId).toList())
                .stream().sorted(Comparator.comparing(Registration::getCreatedAt))
                .collect(Collectors.groupingBy(Registration::getEventId));
        Set<UUID> userIds = new HashSet<>();
        list.forEach(e -> userIds.add(e.getOrganizerId()));
        if (withAttendees) {
            byEvent.values().forEach(rs -> rs.forEach(r -> userIds.add(r.getUserId())));
        }
        Map<UUID, String> names = userLookup.names(userIds);
        Instant now = clock.instant();
        return list.stream().map(e -> {
            List<Registration> rs = byEvent.getOrDefault(e.getId(), List.of());
            List<Registration> confirmed = rs.stream().filter(r -> r.getStatus() == RegistrationStatus.CONFIRMED).toList();
            List<Registration> waiting = rs.stream().filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED).toList();
            Registration mine = rs.stream().filter(r -> r.getUserId().equals(me.id())).findFirst().orElse(null);
            Integer myPosition = mine != null && mine.getStatus() == RegistrationStatus.WAITLISTED
                    ? waiting.indexOf(mine) + 1 : null;
            boolean organizer = e.getOrganizerId().equals(me.id());
            List<AttendeeDto> attendees = withAttendees && (organizer || me.isAdmin())
                    ? rs.stream().map(r -> new AttendeeDto(r.getUserId(), names.get(r.getUserId()), r.getStatus(),
                    r.getStatus() == RegistrationStatus.WAITLISTED ? waiting.indexOf(r) + 1 : null, r.getCreatedAt()))
                    .toList()
                    : null;
            return new EventDto(e.getId(), e.getTitle(), e.getDescription(), e.getLocation(), e.getStartsAt(),
                    e.getCapacity(), e.getRegistrationDeadline(), e.getStatus(), e.getOrganizerId(),
                    names.get(e.getOrganizerId()), confirmed.size(), waiting.size(),
                    Math.max(0, e.getCapacity() - confirmed.size()),
                    e.getStatus() == EventStatus.ACTIVE && !now.isAfter(e.getRegistrationDeadline()),
                    !e.getStartsAt().isAfter(now), organizer, mine == null ? null : mine.getStatus(), myPosition,
                    attendees);
        }).toList();
    }
}
