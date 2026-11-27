package fr.epita.platform.apps.oncall.service;

import fr.epita.platform.apps.oncall.dto.OnCallDtos.*;
import fr.epita.platform.apps.oncall.entity.*;
import fr.epita.platform.apps.oncall.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.IsoWeeks;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Règle des semaines consécutives : toujours appliquée, sauf pour une équipe de 2 membres où elle peut être
 * inévitable (indisponibilité de l'un) ; au-delà de 2 membres, une affectation consécutive est refusée.
 */
@Service
@RequiredArgsConstructor
public class OnCallService {

    private final OnCallTeamRepository teams;
    private final ShiftRepository shifts;
    private final UnavailabilityRepository unavailabilities;
    private final SwapRepository swaps;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- équipes ----------

    @Transactional(readOnly = true)
    public List<TeamDto> myTeams(AuthUser me) {
        List<OnCallTeam> list = me.isAdmin() ? teams.findAllByOrderByNameAsc() : teams.involving(me.id());
        return list.stream().map(t -> toDto(me, t)).toList();
    }

    @Transactional
    public TeamDto create(AuthUser me, TeamRequest req) {
        me.requireAdmin();
        if (teams.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("TEAM_EXISTS", "Une équipe porte déjà ce nom");
        }
        OnCallTeam t = new OnCallTeam();
        apply(t, req);
        return toDto(me, teams.saveAndFlush(t));
    }

    @Transactional
    public TeamDto update(AuthUser me, UUID id, TeamRequest req) {
        me.requireAdmin();
        OnCallTeam t = teams.lock(id).orElseThrow(() -> ApiException.notFound("Équipe"));
        if (!t.getName().equalsIgnoreCase(req.name().trim()) && teams.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("TEAM_EXISTS", "Une équipe porte déjà ce nom");
        }
        apply(t, req);
        return toDto(me, teams.saveAndFlush(t));
    }

    private void apply(OnCallTeam t, TeamRequest req) {
        Set<UUID> members = new LinkedHashSet<>(req.memberIds());
        if (members.size() < 2) {
            throw ValidationException.field("memberIds", "au moins 2 membres distincts");
        }
        userLookup.require(req.managerId());
        members.forEach(userLookup::require);
        t.setName(req.name().trim());
        t.setManagerId(req.managerId());
        t.setMemberIds(new HashSet<>(members));
    }

    // ---------- planning ----------

    @Transactional(readOnly = true)
    public ScheduleDto schedule(AuthUser me, UUID id, String from, String to) {
        OnCallTeam t = visible(me, id);
        LocalDate start = from == null ? currentMonday() : IsoWeeks.monday(from);
        LocalDate end = to == null ? start.plusWeeks(11) : IsoWeeks.monday(to);
        if (end.isBefore(start)) {
            throw ValidationException.field("to", "doit être postérieure à from");
        }
        if (end.isAfter(start.plusWeeks(104))) {
            throw ValidationException.field("to", "104 semaines maximum");
        }
        Map<LocalDate, Shift> byWeek = shifts.findByTeamIdAndWeekStartBetweenOrderByWeekStartAsc(id, start, end).stream()
                .collect(Collectors.toMap(Shift::getWeekStart, Function.identity()));
        Map<LocalDate, List<UUID>> unav = unavailabilities.findByTeamIdAndWeekStartBetween(id, start, end).stream()
                .collect(Collectors.groupingBy(Unavailability::getWeekStart, Collectors.mapping(Unavailability::getUserId, Collectors.toList())));
        List<WeekDto> weeks = new ArrayList<>();
        for (LocalDate w = start; !w.isAfter(end); w = w.plusWeeks(1)) {
            Shift s = byWeek.get(w);
            UUID u = s == null ? null : s.getUserId();
            weeks.add(new WeekDto(IsoWeeks.of(w), w, u, userLookup.name(u), locked(w), me.id().equals(u),
                    unav.getOrDefault(w, List.of()).stream().map(userLookup::name).toList()));
        }
        return new ScheduleDto(id, t.getName(), weeks);
    }

    /** Rotation équitable : à chaque semaine, le membre disponible le moins sollicité (puis le plus anciennement d'astreinte). */
    @Transactional
    public ScheduleDto generate(AuthUser me, UUID id, GenerateRequest req) {
        OnCallTeam t = managerLock(me, id);
        LocalDate first = IsoWeeks.monday(req.fromWeek());
        if (locked(first)) {
            throw ApiException.conflict("WEEK_LOCKED", "La génération doit commencer après la semaine en cours");
        }
        LocalDate last = first.plusWeeks(req.weeks() - 1L);
        List<Shift> history = shifts.findByTeamIdOrderByWeekStartAsc(id);
        Map<LocalDate, Shift> byWeek = history.stream().collect(Collectors.toMap(Shift::getWeekStart, Function.identity()));
        Map<UUID, Long> counts = new HashMap<>();
        Map<UUID, LocalDate> lastShift = new HashMap<>();
        t.getMemberIds().forEach(m -> counts.put(m, 0L));
        // l'historique avant la période compte pour l'équité
        history.stream().filter(s -> s.getWeekStart().isBefore(first) && counts.containsKey(s.getUserId())).forEach(s -> {
            counts.merge(s.getUserId(), 1L, Long::sum);
            lastShift.put(s.getUserId(), s.getWeekStart());
        });
        Set<String> unav = unavailabilities.findByTeamIdAndWeekStartBetween(id, first.minusWeeks(1), last.plusWeeks(1)).stream()
                .map(u -> u.getUserId() + "|" + u.getWeekStart()).collect(Collectors.toSet());
        Map<UUID, String> names = userLookup.names(t.getMemberIds());
        for (LocalDate w = first; !w.isAfter(last); w = w.plusWeeks(1)) {
            Shift existing = byWeek.get(w);
            if (existing != null && !req.overwrite()) {
                counts.merge(existing.getUserId(), 1L, Long::sum);
                lastShift.put(existing.getUserId(), w);
                continue;
            }
            LocalDate week = w;
            UUID previous = Optional.ofNullable(byWeek.get(w.minusWeeks(1))).map(Shift::getUserId).orElse(null);
            UUID next = req.overwrite() && !w.plusWeeks(1).isAfter(last) ? null
                    : Optional.ofNullable(byWeek.get(w.plusWeeks(1))).map(Shift::getUserId).orElse(null);
            List<UUID> available = t.getMemberIds().stream().filter(m -> !unav.contains(m + "|" + week)).toList();
            List<UUID> eligible = available.stream().filter(m -> !m.equals(previous) && !m.equals(next)).toList();
            if (eligible.isEmpty() && t.getMemberIds().size() == 2) {
                eligible = available;
            }
            if (eligible.isEmpty()) {
                throw ApiException.conflict("NO_ELIGIBLE_MEMBER", "Aucun membre disponible pour la semaine " + IsoWeeks.of(week)
                        + " sans enchaîner deux semaines d'astreinte");
            }
            UUID chosen = eligible.stream().min(Comparator.comparing((UUID m) -> counts.get(m))
                    .thenComparing(m -> lastShift.getOrDefault(m, LocalDate.MIN))
                    .thenComparing(m -> names.getOrDefault(m, ""))).orElseThrow();
            Shift s = existing != null ? existing : new Shift();
            s.setTeamId(id);
            s.setWeekStart(w);
            s.setUserId(chosen);
            byWeek.put(w, shifts.save(s));
            counts.merge(chosen, 1L, Long::sum);
            lastShift.put(chosen, w);
        }
        shifts.flush();
        return schedule(me, id, IsoWeeks.of(first), IsoWeeks.of(last));
    }

    @Transactional
    public WeekDto assign(AuthUser me, UUID id, String week, UUID userId) {
        OnCallTeam t = managerLock(me, id);
        LocalDate w = IsoWeeks.monday(week);
        requireUnlocked(w);
        if (!t.getMemberIds().contains(userId)) {
            throw ValidationException.field("userId", "doit être membre de l'équipe");
        }
        if (unavailabilities.existsByTeamIdAndUserIdAndWeekStart(id, userId, w)) {
            throw ApiException.conflict("MEMBER_UNAVAILABLE", userLookup.name(userId) + " est indisponible cette semaine-là");
        }
        Map<LocalDate, UUID> plan = plan(id, w.minusWeeks(1), w.plusWeeks(1));
        plan.put(w, userId);
        checkConsecutive(t, plan, w);
        Shift s = shifts.findByTeamIdAndWeekStart(id, w).orElseGet(Shift::new);
        s.setTeamId(id);
        s.setWeekStart(w);
        s.setUserId(userId);
        shifts.saveAndFlush(s);
        return new WeekDto(IsoWeeks.of(w), w, userId, userLookup.name(userId), false, me.id().equals(userId), List.of());
    }

    @Transactional
    public void unavailable(AuthUser me, UUID id, UnavailabilityRequest req) {
        OnCallTeam t = visible(me, id);
        if (!t.getMemberIds().contains(me.id())) {
            throw ApiException.forbidden("Réservé aux membres de l'équipe");
        }
        if (req.weekStart().getDayOfWeek() != DayOfWeek.MONDAY) {
            throw ValidationException.field("weekStart", "doit être un lundi");
        }
        requireUnlocked(req.weekStart());
        if (unavailabilities.existsByTeamIdAndUserIdAndWeekStart(id, me.id(), req.weekStart())) {
            throw ApiException.conflict("ALREADY_DECLARED", "Indisponibilité déjà déclarée pour cette semaine");
        }
        shifts.findByTeamIdAndWeekStart(id, req.weekStart()).filter(s -> s.getUserId().equals(me.id())).ifPresent(s -> {
            throw ApiException.conflict("MEMBER_UNAVAILABLE", "Vous êtes d'astreinte cette semaine-là : proposez un échange");
        });
        Unavailability u = new Unavailability();
        u.setTeamId(id);
        u.setUserId(me.id());
        u.setWeekStart(req.weekStart());
        u.setReason(req.reason().trim());
        unavailabilities.saveAndFlush(u);
    }

    @Transactional(readOnly = true)
    public List<MyShiftDto> myShifts(AuthUser me) {
        Map<UUID, String> teamNames = teams.involving(me.id()).stream().collect(Collectors.toMap(OnCallTeam::getId, OnCallTeam::getName));
        return shifts.findByUserIdAndWeekStartGreaterThanEqualOrderByWeekStartAsc(me.id(), currentMonday()).stream()
                .map(s -> new MyShiftDto(s.getTeamId(), teamNames.get(s.getTeamId()), IsoWeeks.of(s.getWeekStart()), s.getWeekStart(), locked(s.getWeekStart())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StatDto> stats(AuthUser me, UUID id, Integer year) {
        OnCallTeam t = visible(me, id);
        int y = year == null ? currentMonday().get(IsoFields.WEEK_BASED_YEAR) : year;
        Map<UUID, Long> counts = shifts.findByTeamIdOrderByWeekStartAsc(id).stream()
                .filter(s -> s.getWeekStart().get(IsoFields.WEEK_BASED_YEAR) == y)
                .collect(Collectors.groupingBy(Shift::getUserId, Collectors.counting()));
        Set<UUID> people = new HashSet<>(t.getMemberIds());
        people.addAll(counts.keySet());
        Map<UUID, String> names = userLookup.names(people);
        return people.stream().map(u -> new StatDto(u, names.get(u), counts.getOrDefault(u, 0L)))
                .sorted(Comparator.comparingLong(StatDto::weeks).reversed().thenComparing(StatDto::name, Comparator.nullsLast(String::compareTo))).toList();
    }

    // ---------- échanges ----------

    @Transactional
    public SwapDto requestSwap(AuthUser me, SwapRequest req) {
        OnCallTeam t = teams.lock(req.teamId()).orElseThrow(() -> ApiException.notFound("Équipe"));
        if (!t.getMemberIds().contains(me.id())) {
            throw ApiException.notFound("Équipe");
        }
        LocalDate mine = IsoWeeks.monday(req.myWeek());
        LocalDate other = IsoWeeks.monday(req.otherWeek());
        if (mine.equals(other)) {
            throw ValidationException.field("otherWeek", "doit différer de myWeek");
        }
        if (me.id().equals(req.otherUserId())) {
            throw ValidationException.field("otherUserId", "doit être un collègue");
        }
        Swap s = new Swap();
        s.setTeamId(t.getId());
        s.setRequesterId(me.id());
        s.setMyWeek(mine);
        s.setOtherUserId(req.otherUserId());
        s.setOtherWeek(other);
        check(t, s);
        s.setStatus(SwapStatus.REQUESTED);
        return toDto(me, swaps.saveAndFlush(s));
    }

    @Transactional
    public SwapDto accept(AuthUser me, UUID id) {
        Swap s = swap(id);
        if (!s.getOtherUserId().equals(me.id())) {
            throw ApiException.forbidden("Seul le collègue sollicité peut accepter");
        }
        transition(s, SwapStatus.REQUESTED);
        OnCallTeam t = teams.lock(s.getTeamId()).orElseThrow();
        check(t, s);
        s.setStatus(SwapStatus.ACCEPTED);
        return toDto(me, swaps.saveAndFlush(s));
    }

    /** Effectif seulement ici : les deux semaines sont échangées, après revérification des règles. */
    @Transactional
    public SwapDto approve(AuthUser me, UUID id) {
        Swap s = swap(id);
        OnCallTeam t = teams.lock(s.getTeamId()).orElseThrow();
        if (!t.getManagerId().equals(me.id())) {
            throw ApiException.forbidden("Seul le manager de l'équipe peut valider");
        }
        transition(s, SwapStatus.ACCEPTED);
        check(t, s);
        Shift a = shifts.findByTeamIdAndWeekStart(t.getId(), s.getMyWeek()).orElseThrow();
        Shift b = shifts.findByTeamIdAndWeekStart(t.getId(), s.getOtherWeek()).orElseThrow();
        a.setUserId(s.getOtherUserId());
        b.setUserId(s.getRequesterId());
        shifts.saveAll(List.of(a, b));
        s.setStatus(SwapStatus.APPROVED);
        s.setDecidedBy(me.id());
        shifts.flush();
        return toDto(me, swaps.saveAndFlush(s));
    }

    @Transactional
    public SwapDto reject(AuthUser me, UUID id) {
        Swap s = swap(id);
        OnCallTeam t = teams.findById(s.getTeamId()).orElseThrow();
        boolean colleague = s.getOtherUserId().equals(me.id()) && s.getStatus() == SwapStatus.REQUESTED;
        boolean manager = t.getManagerId().equals(me.id());
        if (!colleague && !manager) {
            throw ApiException.forbidden("Seuls le collègue sollicité (avant acceptation) ou le manager peuvent refuser");
        }
        transition(s, SwapStatus.REQUESTED, SwapStatus.ACCEPTED);
        s.setStatus(SwapStatus.REJECTED);
        s.setDecidedBy(me.id());
        return toDto(me, swaps.saveAndFlush(s));
    }

    @Transactional(readOnly = true)
    public List<SwapDto> list(AuthUser me, String box) {
        List<Swap> list = switch (box == null ? "received" : box) {
            case "sent" -> swaps.findByRequesterIdOrderByCreatedAtDesc(me.id());
            case "received" -> swaps.findByOtherUserIdOrderByCreatedAtDesc(me.id());
            case "to-approve" -> {
                List<UUID> managed = teams.involving(me.id()).stream().filter(t -> t.getManagerId().equals(me.id())).map(OnCallTeam::getId).toList();
                yield managed.isEmpty() ? List.of() : swaps.findByTeamIdInAndStatusOrderByCreatedAtAsc(managed, SwapStatus.ACCEPTED);
            }
            default -> throw ValidationException.field("box", "doit valoir sent, received ou to-approve");
        };
        return list.stream().map(s -> toDto(me, s)).toList();
    }

    /** Titulaires inchangés, semaines non verrouillées, disponibilités et semaines consécutives après échange. */
    private void check(OnCallTeam t, Swap s) {
        requireUnlocked(s.getMyWeek());
        requireUnlocked(s.getOtherWeek());
        if (!t.getMemberIds().contains(s.getOtherUserId())) {
            throw ValidationException.field("otherUserId", "doit être membre de l'équipe");
        }
        UUID holderMine = shifts.findByTeamIdAndWeekStart(t.getId(), s.getMyWeek()).map(Shift::getUserId).orElse(null);
        UUID holderOther = shifts.findByTeamIdAndWeekStart(t.getId(), s.getOtherWeek()).map(Shift::getUserId).orElse(null);
        if (!s.getRequesterId().equals(holderMine)) {
            throw ApiException.conflict("NOT_ON_CALL", "Le demandeur n'est pas (ou plus) d'astreinte la semaine " + IsoWeeks.of(s.getMyWeek()));
        }
        if (!s.getOtherUserId().equals(holderOther)) {
            throw ApiException.conflict("NOT_ON_CALL", userLookup.name(s.getOtherUserId()) + " n'est pas d'astreinte la semaine " + IsoWeeks.of(s.getOtherWeek()));
        }
        if (unavailabilities.existsByTeamIdAndUserIdAndWeekStart(t.getId(), s.getRequesterId(), s.getOtherWeek())
                || unavailabilities.existsByTeamIdAndUserIdAndWeekStart(t.getId(), s.getOtherUserId(), s.getMyWeek())) {
            throw ApiException.conflict("MEMBER_UNAVAILABLE", "L'un des deux est indisponible sur sa nouvelle semaine");
        }
        LocalDate from = (s.getMyWeek().isBefore(s.getOtherWeek()) ? s.getMyWeek() : s.getOtherWeek()).minusWeeks(1);
        LocalDate to = (s.getMyWeek().isAfter(s.getOtherWeek()) ? s.getMyWeek() : s.getOtherWeek()).plusWeeks(1);
        Map<LocalDate, UUID> plan = plan(t.getId(), from, to);
        plan.put(s.getMyWeek(), s.getOtherUserId());
        plan.put(s.getOtherWeek(), s.getRequesterId());
        checkConsecutive(t, plan, s.getMyWeek());
        checkConsecutive(t, plan, s.getOtherWeek());
    }

    private void checkConsecutive(OnCallTeam t, Map<LocalDate, UUID> plan, LocalDate w) {
        if (t.getMemberIds().size() == 2) {
            return;
        }
        UUID u = plan.get(w);
        if (u != null && (u.equals(plan.get(w.minusWeeks(1))) || u.equals(plan.get(w.plusWeeks(1))))) {
            throw ApiException.conflict("CONSECUTIVE_ON_CALL", userLookup.name(u) + " serait d'astreinte deux semaines de suite");
        }
    }

    private Map<LocalDate, UUID> plan(UUID teamId, LocalDate from, LocalDate to) {
        Map<LocalDate, UUID> plan = new HashMap<>();
        shifts.findByTeamIdAndWeekStartBetweenOrderByWeekStartAsc(teamId, from, to).forEach(s -> plan.put(s.getWeekStart(), s.getUserId()));
        return plan;
    }

    // ---------- garde-fous ----------

    private LocalDate currentMonday() {
        return LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private boolean locked(LocalDate weekStart) {
        return !weekStart.isAfter(currentMonday());
    }

    private void requireUnlocked(LocalDate weekStart) {
        if (locked(weekStart)) {
            throw ApiException.conflict("WEEK_LOCKED", "La semaine " + IsoWeeks.of(weekStart) + " est passée ou en cours");
        }
    }

    private OnCallTeam visible(AuthUser me, UUID id) {
        OnCallTeam t = teams.findById(id).orElseThrow(() -> ApiException.notFound("Équipe"));
        if (!me.isAdmin() && !t.getManagerId().equals(me.id()) && !t.getMemberIds().contains(me.id())) {
            throw ApiException.notFound("Équipe");
        }
        return t;
    }

    private OnCallTeam managerLock(AuthUser me, UUID id) {
        OnCallTeam t = teams.lock(id).orElseThrow(() -> ApiException.notFound("Équipe"));
        if (!t.getManagerId().equals(me.id())) {
            throw ApiException.forbidden("Réservé au manager de l'équipe");
        }
        return t;
    }

    private Swap swap(UUID id) {
        return swaps.findById(id).orElseThrow(() -> ApiException.notFound("Échange"));
    }

    private void transition(Swap s, SwapStatus... allowed) {
        if (!Arrays.asList(allowed).contains(s.getStatus())) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Échange déjà " + s.getStatus());
        }
    }

    private TeamDto toDto(AuthUser me, OnCallTeam t) {
        Map<UUID, String> names = userLookup.names(t.getMemberIds());
        List<MemberDto> members = t.getMemberIds().stream().map(u -> new MemberDto(u, names.get(u)))
                .sorted(Comparator.comparing(MemberDto::name, Comparator.nullsLast(String::compareTo))).toList();
        return new TeamDto(t.getId(), t.getName(), t.getManagerId(), userLookup.name(t.getManagerId()), members,
                t.getManagerId().equals(me.id()), t.getMemberIds().contains(me.id()));
    }

    private SwapDto toDto(AuthUser me, Swap s) {
        OnCallTeam t = teams.findById(s.getTeamId()).orElseThrow();
        boolean manager = t.getManagerId().equals(me.id());
        boolean colleague = s.getOtherUserId().equals(me.id());
        return new SwapDto(s.getId(), t.getId(), t.getName(), s.getRequesterId(), userLookup.name(s.getRequesterId()), IsoWeeks.of(s.getMyWeek()),
                s.getOtherUserId(), userLookup.name(s.getOtherUserId()), IsoWeeks.of(s.getOtherWeek()), s.getStatus(),
                s.getDecidedBy() == null ? null : userLookup.name(s.getDecidedBy()),
                colleague && s.getStatus() == SwapStatus.REQUESTED, manager && s.getStatus() == SwapStatus.ACCEPTED,
                (colleague && s.getStatus() == SwapStatus.REQUESTED) || (manager && (s.getStatus() == SwapStatus.REQUESTED || s.getStatus() == SwapStatus.ACCEPTED)));
    }
}
