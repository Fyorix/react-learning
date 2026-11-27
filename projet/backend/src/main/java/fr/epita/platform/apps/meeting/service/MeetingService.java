package fr.epita.platform.apps.meeting.service;

import fr.epita.platform.apps.meeting.dto.MeetingDtos.*;
import fr.epita.platform.apps.meeting.entity.*;
import fr.epita.platform.apps.meeting.repository.MeetingAnswerRepository;
import fr.epita.platform.apps.meeting.repository.MeetingPollRepository;
import fr.epita.platform.apps.meeting.repository.MeetingSlotRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sondage de créneaux : suggestion = plus de YES, puis plus de IF_NEEDED, puis le plus tôt. La décision fige les réponses.
 * Relance des non-répondants au plus une fois par 24 h.
 */
@Service
@RequiredArgsConstructor
public class MeetingService {

    static final Duration REMINDER_INTERVAL = Duration.ofHours(24);

    private final MeetingPollRepository polls;
    private final MeetingSlotRepository slots;
    private final MeetingAnswerRepository answers;
    private final UserLookup userLookup;
    private final Clock clock;

    @Transactional
    public PollDto create(AuthUser me, PollRequest req) {
        MeetingPoll p = new MeetingPoll();
        p.setOrganizerId(me.id());
        p.setStatus(PollStatus.OPEN);
        apply(me, p, req);
        polls.saveAndFlush(p);
        saveSlots(p, req);
        return toDto(me, p);
    }

    @Transactional
    public PollDto update(AuthUser me, UUID id, PollRequest req) {
        MeetingPoll p = organizerPoll(me, id);
        open(p);
        if (!answers.findByIdSlotIdIn(slotIds(p)).isEmpty()) {
            throw ApiException.conflict("POLL_HAS_ANSWERS", "Des participants ont déjà répondu : la proposition n'est plus modifiable");
        }
        apply(me, p, req);
        slots.deleteByPollId(p.getId());
        slots.flush();
        polls.saveAndFlush(p);
        saveSlots(p, req);
        return toDto(me, p);
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        polls.delete(organizerPoll(me, id));
    }

    @Transactional(readOnly = true)
    public List<PollSummary> list(AuthUser me, String role, PollStatus status) {
        List<MeetingPoll> list;
        if ("organizer".equals(role)) {
            list = polls.findByOrganizerIdOrderByCreatedAtDesc(me.id());
        } else if ("participant".equals(role)) {
            list = polls.ofParticipant(me.id()).stream().filter(p -> !p.getOrganizerId().equals(me.id())).toList();
        } else if (role == null) {
            list = polls.ofParticipant(me.id());
        } else {
            throw ValidationException.field("role", "doit valoir organizer ou participant");
        }
        return list.stream().filter(p -> status == null || p.getStatus() == status).map(p -> summary(me, p)).toList();
    }

    @Transactional(readOnly = true)
    public PollDto get(AuthUser me, UUID id) {
        return toDto(me, visible(me, polls.findById(id)));
    }

    @Transactional
    public PollDto answer(AuthUser me, UUID id, List<AnswerInput> input) {
        MeetingPoll p = visible(me, polls.lock(id));
        if (p.getStatus() == PollStatus.DECIDED) {
            throw ApiException.conflict("POLL_DECIDED", "La réunion est fixée : les réponses sont figées");
        }
        Set<UUID> ids = slotIds(p);
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < input.size(); i++) {
            if (!ids.contains(input.get(i).slotId())) {
                throw ValidationException.field("answers[" + i + "].slotId", "créneau étranger à la proposition");
            }
            if (!seen.add(input.get(i).slotId())) {
                throw ValidationException.field("answers[" + i + "].slotId", "créneau en double");
            }
        }
        answers.deleteAllById(ids.stream().map(s -> new MeetingAnswer.Key(s, me.id())).toList());
        answers.flush();
        input.forEach(a -> answers.save(new MeetingAnswer(new MeetingAnswer.Key(a.slotId(), me.id()), a.answer())));
        answers.flush();
        return toDto(me, p);
    }

    @Transactional
    public PollDto decide(AuthUser me, UUID id, UUID slotId) {
        MeetingPoll p = organizerPollLocked(me, id);
        open(p);
        MeetingSlot s = slots.findById(slotId).filter(x -> x.getPollId().equals(p.getId())).orElseThrow(() -> ApiException.notFound("Créneau"));
        if (!s.getStartAt().isAfter(clock.instant())) {
            throw ApiException.conflict("SLOT_IN_PAST", "Ce créneau est passé");
        }
        p.setStatus(PollStatus.DECIDED);
        p.setDecidedSlotId(slotId);
        polls.saveAndFlush(p);
        return toDto(me, p);
    }

    @Transactional
    public ReminderDto remind(AuthUser me, UUID id) {
        MeetingPoll p = organizerPollLocked(me, id);
        open(p);
        Instant now = clock.instant();
        if (p.getLastReminderAt() != null && p.getLastReminderAt().plus(REMINDER_INTERVAL).isAfter(now)) {
            throw ApiException.conflict("REMINDER_TOO_SOON", "Une relance a déjà été envoyée il y a moins de 24 h");
        }
        Set<UUID> answered = answers.findByIdSlotIdIn(slotIds(p)).stream().map(a -> a.getId().getUserId()).collect(Collectors.toSet());
        List<UUID> missing = p.getParticipantIds().stream().filter(u -> !answered.contains(u)).toList();
        if (missing.isEmpty()) {
            throw ApiException.conflict("NOTHING_TO_REMIND", "Tous les participants ont répondu");
        }
        p.setLastReminderAt(now);
        polls.saveAndFlush(p);
        Map<UUID, String> names = userLookup.names(missing);
        return new ReminderDto(missing.stream().map(names::get).sorted(Comparator.nullsLast(String::compareTo)).toList(), now);
    }

    // ---------- règles ----------

    private void apply(AuthUser me, MeetingPoll p, PollRequest req) {
        Instant now = clock.instant();
        Set<Instant> seen = new HashSet<>();
        for (int i = 0; i < req.slots().size(); i++) {
            Instant start = req.slots().get(i).start().truncatedTo(ChronoUnit.MINUTES);
            if (!start.isAfter(now)) {
                throw ValidationException.field("slots[" + i + "].start", "doit être dans le futur");
            }
            if (!seen.add(start)) {
                throw ValidationException.field("slots[" + i + "].start", "créneau en double");
            }
        }
        Set<UUID> participants = new HashSet<>(req.participantIds());
        participants.forEach(userLookup::require);
        participants.add(me.id());
        p.setTitle(req.title().trim());
        p.setDurationMinutes(req.durationMinutes());
        p.setLocation(req.location() == null || req.location().isBlank() ? null : req.location().trim());
        p.setParticipantIds(participants);
    }

    private void saveSlots(MeetingPoll p, PollRequest req) {
        req.slots().forEach(s -> slots.save(new MeetingSlot(UUID.randomUUID(), p.getId(), s.start().truncatedTo(ChronoUnit.MINUTES))));
        slots.flush();
    }

    private void open(MeetingPoll p) {
        if (p.getStatus() == PollStatus.DECIDED) {
            throw ApiException.conflict("POLL_DECIDED", "La réunion est déjà fixée");
        }
    }

    /** Créneau suggéré : plus de YES, puis plus de IF_NEEDED, puis le plus tôt. */
    static Optional<SlotDto> suggestion(List<SlotDto> list) {
        return list.stream().filter(s -> !s.past()).min(Comparator.comparingInt(SlotDto::yes).reversed()
                .thenComparing(Comparator.comparingInt(SlotDto::ifNeeded).reversed()).thenComparing(SlotDto::start));
    }

    private MeetingPoll visible(AuthUser me, Optional<MeetingPoll> poll) {
        MeetingPoll p = poll.orElseThrow(() -> ApiException.notFound("Proposition"));
        if (!p.getParticipantIds().contains(me.id())) {
            throw ApiException.forbidden("Réservé aux participants invités");
        }
        return p;
    }

    private MeetingPoll organizerPoll(AuthUser me, UUID id) {
        return checkOrganizer(me, polls.findById(id));
    }

    private MeetingPoll organizerPollLocked(AuthUser me, UUID id) {
        return checkOrganizer(me, polls.lock(id));
    }

    private MeetingPoll checkOrganizer(AuthUser me, Optional<MeetingPoll> poll) {
        MeetingPoll p = poll.orElseThrow(() -> ApiException.notFound("Proposition"));
        if (!p.getOrganizerId().equals(me.id())) {
            throw ApiException.forbidden("Réservé à l'organisateur");
        }
        return p;
    }

    private Set<UUID> slotIds(MeetingPoll p) {
        return slots.findByPollIdOrderByStartAtAsc(p.getId()).stream().map(MeetingSlot::getId).collect(Collectors.toSet());
    }

    // ---------- conversions ----------

    private PollSummary summary(AuthUser me, MeetingPoll p) {
        Set<UUID> ids = slotIds(p);
        Set<UUID> respondents = answers.findByIdSlotIdIn(ids).stream().map(a -> a.getId().getUserId()).collect(Collectors.toSet());
        Instant decided = p.getDecidedSlotId() == null ? null : slots.findById(p.getDecidedSlotId()).map(MeetingSlot::getStartAt).orElse(null);
        return new PollSummary(p.getId(), p.getTitle(), p.getDurationMinutes(), userLookup.name(p.getOrganizerId()), p.getStatus(),
                p.getOrganizerId().equals(me.id()), respondents.contains(me.id()), p.getParticipantIds().size(), respondents.size(), decided, p.getCreatedAt());
    }

    private PollDto toDto(AuthUser me, MeetingPoll p) {
        Instant now = clock.instant();
        List<MeetingSlot> ss = slots.findByPollIdOrderByStartAtAsc(p.getId());
        List<MeetingAnswer> all = answers.findByIdSlotIdIn(ss.stream().map(MeetingSlot::getId).toList());
        Map<UUID, Map<UUID, Availability>> byUser = new HashMap<>();
        all.forEach(a -> byUser.computeIfAbsent(a.getId().getUserId(), k -> new HashMap<>()).put(a.getId().getSlotId(), a.getAnswer()));
        List<SlotDto> base = ss.stream().map(s -> {
            List<Availability> av = all.stream().filter(a -> a.getId().getSlotId().equals(s.getId())).map(MeetingAnswer::getAnswer).toList();
            return new SlotDto(s.getId(), s.getStartAt(), s.getStartAt().plusSeconds(p.getDurationMinutes() * 60L),
                    (int) av.stream().filter(a -> a == Availability.YES).count(), (int) av.stream().filter(a -> a == Availability.IF_NEEDED).count(),
                    (int) av.stream().filter(a -> a == Availability.NO).count(), false, s.getId().equals(p.getDecidedSlotId()), !s.getStartAt().isAfter(now));
        }).toList();
        UUID suggested = suggestion(base).map(SlotDto::id).orElse(null);
        List<SlotDto> list = base.stream().map(s -> new SlotDto(s.id(), s.start(), s.end(), s.yes(), s.ifNeeded(), s.no(), s.id().equals(suggested), s.decided(), s.past())).toList();
        Map<UUID, String> names = userLookup.names(p.getParticipantIds());
        List<ParticipantDto> participants = p.getParticipantIds().stream()
                .map(u -> new ParticipantDto(u, names.get(u), u.equals(p.getOrganizerId()), byUser.containsKey(u), byUser.getOrDefault(u, Map.of())))
                .sorted(Comparator.comparing(ParticipantDto::organizer).reversed().thenComparing(ParticipantDto::name, Comparator.nullsLast(String::compareTo))).toList();
        return new PollDto(p.getId(), p.getTitle(), p.getDurationMinutes(), p.getLocation(), p.getOrganizerId(), userLookup.name(p.getOrganizerId()),
                p.getStatus(), p.getOrganizerId().equals(me.id()), list, participants, suggested, p.getDecidedSlotId(), p.getLastReminderAt());
    }
}
