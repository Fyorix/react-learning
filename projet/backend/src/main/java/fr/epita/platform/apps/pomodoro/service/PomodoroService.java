package fr.epita.platform.apps.pomodoro.service;

import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.DayCountDto;
import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.MemberDto;
import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.RoomRequest;
import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.RoomSummaryDto;
import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.StateDto;
import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.StatsDto;
import fr.epita.platform.apps.pomodoro.entity.CompletedPomodoro;
import fr.epita.platform.apps.pomodoro.entity.Phase;
import fr.epita.platform.apps.pomodoro.entity.PomodoroMember;
import fr.epita.platform.apps.pomodoro.entity.PomodoroRoom;
import fr.epita.platform.apps.pomodoro.repository.CompletedPomodoroRepository;
import fr.epita.platform.apps.pomodoro.repository.PomodoroMemberRepository;
import fr.epita.platform.apps.pomodoro.repository.PomodoroRoomRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PomodoroService {

    static final Duration AWAY_AFTER = Duration.ofSeconds(60);
    static final Duration IDLE_AFTER = Duration.ofMinutes(10);

    private final PomodoroRoomRepository rooms;
    private final PomodoroMemberRepository members;
    private final CompletedPomodoroRepository completed;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- salles ----------

    @Transactional(readOnly = true)
    public List<RoomSummaryDto> myRooms(AuthUser me) {
        var mine = members.findByIdUserId(me.id()).stream().map(m -> m.getId().getRoomId()).collect(Collectors.toSet());
        return rooms.findAllByOrderByNameAsc().stream().filter(r -> mine.contains(r.getId()))
                .map(r -> new RoomSummaryDto(r.getId(), r.getName(), userLookup.name(r.getHostId()), r.getPhase(),
                        members.findByIdRoomId(r.getId()).size())).toList();
    }

    @Transactional
    public StateDto create(AuthUser me, RoomRequest req) {
        PomodoroRoom r = new PomodoroRoom();
        r.setHostId(me.id());
        applySettings(r, req);
        rooms.saveAndFlush(r);
        members.saveAndFlush(new PomodoroMember(r.getId(), me.id()));
        return state(r, me);
    }

    @Transactional
    public StateDto update(AuthUser me, UUID id, RoomRequest req) {
        PomodoroRoom r = hosted(me, id);
        refresh(r);
        if (r.getPhase() != Phase.IDLE) {
            throw ApiException.conflict("SESSION_RUNNING", "Arrêtez la session avant de modifier les durées");
        }
        applySettings(r, req);
        return state(r, me);
    }

    @Transactional
    public StateDto invite(AuthUser me, UUID id, List<UUID> userIds) {
        PomodoroRoom r = hosted(me, id);
        for (UUID u : userIds) {
            userLookup.require(u);
            if (!members.existsById(new PomodoroMember.Key(id, u))) {
                members.save(new PomodoroMember(id, u));
            }
        }
        members.flush();
        return state(r, me);
    }

    // ---------- session ----------

    @Transactional
    public StateDto getState(AuthUser me, UUID id) {
        PomodoroRoom r = memberRoom(me, id);
        refresh(r);
        return state(r, me);
    }

    @Transactional
    public StateDto start(AuthUser me, UUID id) {
        PomodoroRoom r = hosted(me, id);
        refresh(r);
        if (r.getPhase() != Phase.IDLE) {
            throw ApiException.conflict("SESSION_RUNNING", "La session est déjà démarrée");
        }
        enter(r, Phase.FOCUS, clock.instant());
        return state(r, me);
    }

    @Transactional
    public StateDto pause(AuthUser me, UUID id) {
        PomodoroRoom r = hosted(me, id);
        refresh(r);
        if (!r.getPhase().running()) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Aucune phase en cours à mettre en pause");
        }
        Instant now = clock.instant();
        r.setPausedRemainingSeconds(Math.max(0, Duration.between(now, r.getPhaseEndsAt()).toSeconds()));
        r.setPausedPhase(r.getPhase());
        r.setPhase(Phase.PAUSED);
        r.setPhaseEndsAt(null);
        return state(r, me);
    }

    @Transactional
    public StateDto resume(AuthUser me, UUID id) {
        PomodoroRoom r = hosted(me, id);
        if (r.getPhase() != Phase.PAUSED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "La session n'est pas en pause");
        }
        Instant now = clock.instant();
        // la durée de la pause décale aussi le début de phase : la présence reste exigée sur toute la phase
        Duration elapsed = r.durationOf(r.getPausedPhase()).minusSeconds(r.getPausedRemainingSeconds());
        r.setPhase(r.getPausedPhase());
        r.setPhaseStartedAt(now.minus(elapsed));
        r.setPhaseEndsAt(now.plusSeconds(r.getPausedRemainingSeconds()));
        r.setPausedPhase(null);
        r.setPausedRemainingSeconds(null);
        return state(r, me);
    }

    /** Passe à la phase suivante sans créditer le focus interrompu. */
    @Transactional
    public StateDto skip(AuthUser me, UUID id) {
        PomodoroRoom r = hosted(me, id);
        refresh(r);
        Phase current = r.getPhase() == Phase.PAUSED ? r.getPausedPhase() : r.getPhase();
        if (current == null || !current.running()) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Aucune phase en cours");
        }
        r.setPausedPhase(null);
        r.setPausedRemainingSeconds(null);
        advance(r, current, clock.instant(), false);
        return state(r, me);
    }

    @Transactional
    public StateDto stop(AuthUser me, UUID id) {
        PomodoroRoom r = hosted(me, id);
        toIdle(r);
        return state(r, me);
    }

    @Transactional
    public StateDto heartbeat(AuthUser me, UUID id) {
        PomodoroRoom r = memberRoom(me, id);
        Instant now = clock.instant();
        PomodoroMember m = members.findById(new PomodoroMember.Key(id, me.id())).orElseThrow();
        if (m.getLastSeenAt() == null || m.getLastSeenAt().plus(AWAY_AFTER).isBefore(now)) {
            m.setPresentSince(now);
        }
        m.setLastSeenAt(now);
        members.saveAndFlush(m);
        refresh(r);
        return state(r, me);
    }

    @Transactional(readOnly = true)
    public StatsDto stats(AuthUser me, String period) {
        String p = period == null ? "week" : period;
        int days = switch (p) {
            case "week" -> 7;
            case "month" -> 30;
            default -> throw ValidationException.field("period", "Valeurs possibles : week, month");
        };
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, Long> counts = completed.findByUserIdAndCompletedAtAfter(me.id(), from.atStartOfDay(zone).toInstant())
                .stream().collect(Collectors.groupingBy(c -> LocalDate.ofInstant(c.getCompletedAt(), zone), TreeMap::new, Collectors.counting()));
        List<DayCountDto> list = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            list.add(new DayCountDto(d, counts.getOrDefault(d, 0L)));
        }
        return new StatsDto(p, list.stream().mapToLong(DayCountDto::count).sum(), list);
    }

    // ---------- machine à états ----------

    /**
     * Rattrape les phases terminées depuis la dernière lecture (calcul à partir de l'heure de fin, jamais d'un compteur).
     * Sans membre présent pendant 10 minutes, la salle repasse en IDLE.
     */
    void refresh(PomodoroRoom r) {
        Instant now = clock.instant();
        if (r.getPhase() != Phase.IDLE) {
            Instant lastSeen = members.findByIdRoomId(r.getId()).stream().map(PomodoroMember::getLastSeenAt)
                    .filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(r.getPhaseStartedAt());
            if (lastSeen != null && lastSeen.plus(IDLE_AFTER).isBefore(now)) {
                toIdle(r);
                return;
            }
        }
        int guard = 0;
        while (r.getPhase().running() && !r.getPhaseEndsAt().isAfter(now) && guard++ < 500) {
            advance(r, r.getPhase(), r.getPhaseEndsAt(), true);
        }
    }

    private void advance(PomodoroRoom r, Phase ended, Instant at, boolean naturalEnd) {
        if (ended == Phase.FOCUS) {
            if (naturalEnd) {
                creditPresentMembers(r, at);
            }
            r.setCycle(r.getCycle() + 1);
            enter(r, r.getCycle() % r.getCyclesBeforeLongBreak() == 0 ? Phase.LONG_BREAK : Phase.SHORT_BREAK, at);
        } else {
            enter(r, Phase.FOCUS, at);
        }
    }

    /** Un pomodoro n'est compté qu'aux membres présents pendant toute la phase de focus. */
    private void creditPresentMembers(PomodoroRoom r, Instant focusEnd) {
        for (PomodoroMember m : members.findByIdRoomId(r.getId())) {
            boolean presentAllAlong = m.getPresentSince() != null && !m.getPresentSince().isAfter(r.getPhaseStartedAt())
                    && m.getLastSeenAt() != null && !m.getLastSeenAt().plus(AWAY_AFTER).isBefore(focusEnd);
            if (presentAllAlong) {
                CompletedPomodoro c = new CompletedPomodoro();
                c.setRoomId(r.getId());
                c.setUserId(m.getId().getUserId());
                c.setCompletedAt(focusEnd);
                completed.save(c);
            }
        }
    }

    private static void enter(PomodoroRoom r, Phase p, Instant at) {
        r.setPhase(p);
        r.setPhaseStartedAt(at);
        r.setPhaseEndsAt(at.plus(r.durationOf(p)));
    }

    private static void toIdle(PomodoroRoom r) {
        r.setPhase(Phase.IDLE);
        r.setPausedPhase(null);
        r.setPausedRemainingSeconds(null);
        r.setPhaseStartedAt(null);
        r.setPhaseEndsAt(null);
        r.setCycle(0);
    }

    // ---------- outils ----------

    private static void applySettings(PomodoroRoom r, RoomRequest req) {
        r.setName(req.name().trim());
        r.setFocusMinutes(req.focusMinutes() == null ? 25 : req.focusMinutes());
        r.setShortBreakMinutes(req.shortBreakMinutes() == null ? 5 : req.shortBreakMinutes());
        r.setLongBreakMinutes(req.longBreakMinutes() == null ? 15 : req.longBreakMinutes());
        r.setCyclesBeforeLongBreak(req.cyclesBeforeLongBreak() == null ? 4 : req.cyclesBeforeLongBreak());
    }

    private PomodoroRoom memberRoom(AuthUser me, UUID id) {
        PomodoroRoom r = rooms.lock(id).orElseThrow(() -> ApiException.notFound("Salle"));
        if (!members.existsById(new PomodoroMember.Key(id, me.id()))) {
            throw ApiException.forbidden("Réservé aux membres de la salle");
        }
        return r;
    }

    private PomodoroRoom hosted(AuthUser me, UUID id) {
        PomodoroRoom r = memberRoom(me, id);
        if (!r.getHostId().equals(me.id())) {
            throw ApiException.forbidden("Seul l'hôte pilote la session");
        }
        return r;
    }

    private StateDto state(PomodoroRoom r, AuthUser me) {
        Instant now = clock.instant();
        List<MemberDto> list = members.findByIdRoomId(r.getId()).stream().map(m -> {
            UUID u = m.getId().getUserId();
            boolean present = m.getLastSeenAt() != null && !m.getLastSeenAt().plus(AWAY_AFTER).isBefore(now);
            String presence = !present ? "AWAY" : r.getPhase() == Phase.FOCUS ? "FOCUS"
                    : r.getPhase() == Phase.SHORT_BREAK || r.getPhase() == Phase.LONG_BREAK ? "BREAK" : "PRESENT";
            return new MemberDto(u, userLookup.name(u), u.equals(r.getHostId()), presence);
        }).sorted((a, b) -> Boolean.compare(b.host(), a.host())).toList();
        Long remaining = r.getPhase() == Phase.PAUSED ? r.getPausedRemainingSeconds()
                : r.getPhaseEndsAt() == null ? null : Math.max(0, Duration.between(now, r.getPhaseEndsAt()).toSeconds());
        return new StateDto(r.getId(), r.getName(), r.getHostId(), r.getPhase(), r.getPausedPhase(), r.getPhaseEndsAt(),
                remaining, now, r.getCycle(), r.getCyclesBeforeLongBreak(), r.getFocusMinutes(), r.getShortBreakMinutes(),
                r.getLongBreakMinutes(), list, r.getHostId().equals(me.id()));
    }
}
