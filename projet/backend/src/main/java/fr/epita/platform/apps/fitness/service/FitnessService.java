package fr.epita.platform.apps.fitness.service;

import fr.epita.platform.apps.fitness.dto.FitnessDtos.*;
import fr.epita.platform.apps.fitness.entity.FitnessBooking;
import fr.epita.platform.apps.fitness.entity.FitnessBookingStatus;
import fr.epita.platform.apps.fitness.entity.FitnessClass;
import fr.epita.platform.apps.fitness.repository.FitnessBookingRepository;
import fr.epita.platform.apps.fitness.repository.FitnessClassRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Crédits : 8 par mois civil (mois du cours, heure de Paris), non reportés ; une réservation confirmée en consomme 1.
 * Annulation gratuite jusqu'à 2 h avant ; la place libérée promeut le premier en attente qui a encore un crédit.
 */
@Service
@RequiredArgsConstructor
public class FitnessService {

    public static final String ROLES = "fitness_roles";
    public static final String COACH = "COACH";
    static final int CREDITS_PER_MONTH = 8;
    static final int DAILY_LIMIT = 2;
    static final Duration FREE_CANCEL = Duration.ofHours(2);
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Set<FitnessBookingStatus> ACTIVE = EnumSet.of(FitnessBookingStatus.CONFIRMED, FitnessBookingStatus.WAITLISTED);

    private final FitnessClassRepository classes;
    private final FitnessBookingRepository bookings;
    private final AppRoles roles;
    private final UserLookup userLookup;
    private final Clock clock;

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(COACH));
    }

    // ---------- planning ----------

    @Transactional(readOnly = true)
    public List<ClassSummary> planning(AuthUser me, Instant from, Instant to, String type) {
        Instant start = from == null ? clock.instant() : from;
        Instant end = to == null ? start.plus(Duration.ofDays(7)) : to;
        if (!end.isAfter(start)) {
            throw ValidationException.field("to", "doit être postérieure à from");
        }
        return classes.findByStartsAtBetweenOrderByStartsAtAsc(start, end).stream()
                .filter(c -> type == null || type.isBlank() || c.getType().equalsIgnoreCase(type))
                .map(c -> summary(me, c)).toList();
    }

    @Transactional
    public ClassSummary create(AuthUser me, ClassRequest req) {
        roles.require(ROLES, me, COACH);
        UUID coach = req.coachId() == null ? me.id() : req.coachId();
        if (!coach.equals(me.id()) && !me.isAdmin()) {
            throw ValidationException.field("coachId", "un coach crée ses propres cours");
        }
        userLookup.require(coach);
        if (!coach.equals(me.id()) && !roles.hasExplicit(ROLES, coach, COACH)) {
            throw ValidationException.field("coachId", "doit avoir le rôle COACH");
        }
        if (!req.startsAt().isAfter(clock.instant())) {
            throw ValidationException.field("startsAt", "doit être dans le futur");
        }
        FitnessClass c = new FitnessClass();
        c.setType(req.type().trim());
        c.setCoachId(coach);
        c.setStartsAt(req.startsAt());
        c.setDurationMinutes(req.durationMinutes());
        c.setCapacity(req.capacity());
        return summary(me, classes.saveAndFlush(c));
    }

    @Transactional(readOnly = true)
    public ClassDto get(AuthUser me, UUID id) {
        FitnessClass c = find(id);
        List<FitnessBooking> bs = bookings.findByClassIdOrderByCreatedAtAsc(id);
        Optional<FitnessBooking> mine = bs.stream().filter(b -> b.getUserId().equals(me.id())).findFirst();
        Integer position = mine.filter(b -> b.getStatus() == FitnessBookingStatus.WAITLISTED).map(b -> waitPosition(bs, b)).orElse(null);
        boolean coachView = me.isAdmin() || c.getCoachId().equals(me.id());
        List<AttendeeDto> attendees = coachView ? bs.stream().filter(b -> ACTIVE.contains(b.getStatus()))
                .map(b -> new AttendeeDto(b.getUserId(), userLookup.name(b.getUserId()), b.getStatus(),
                        b.getStatus() == FitnessBookingStatus.WAITLISTED ? waitPosition(bs, b) : 0)).toList() : List.of();
        boolean free = clock.instant().isBefore(c.getStartsAt().minus(FREE_CANCEL));
        return new ClassDto(summary(me, c), position, free, attendees);
    }

    @Transactional
    public ClassSummary cancelClass(AuthUser me, UUID id) {
        FitnessClass c = classes.lock(id).orElseThrow(() -> ApiException.notFound("Cours"));
        if (!me.isAdmin() && !c.getCoachId().equals(me.id())) {
            throw ApiException.forbidden("Réservé au coach du cours ou à un administrateur");
        }
        if (c.isCancelled()) {
            throw ApiException.conflict("CLASS_CANCELLED", "Cours déjà annulé");
        }
        if (!c.getStartsAt().isAfter(clock.instant())) {
            throw ApiException.conflict("CLASS_STARTED", "Le cours a déjà commencé");
        }
        c.setCancelled(true);
        // crédits rendus à tous les inscrits
        bookings.findByClassIdOrderByCreatedAtAsc(id).stream().filter(b -> ACTIVE.contains(b.getStatus())).forEach(b -> {
            b.setStatus(FitnessBookingStatus.CANCELLED);
            b.setDebited(false);
            b.setUpdatedAt(clock.instant());
            bookings.save(b);
        });
        bookings.flush();
        return summary(me, classes.saveAndFlush(c));
    }

    // ---------- réservations ----------

    @Transactional
    public BookingResult book(AuthUser me, UUID id) {
        FitnessClass c = classes.lock(id).orElseThrow(() -> ApiException.notFound("Cours"));
        if (c.isCancelled()) {
            throw ApiException.conflict("CLASS_CANCELLED", "Ce cours est annulé");
        }
        if (!c.getStartsAt().isAfter(clock.instant())) {
            throw ApiException.conflict("CLASS_STARTED", "Ce cours est passé ou commencé");
        }
        Optional<FitnessBooking> existing = bookings.findByClassIdAndUserId(id, me.id());
        if (existing.filter(b -> ACTIVE.contains(b.getStatus())).isPresent()) {
            throw ApiException.conflict("ALREADY_BOOKED", "Vous êtes déjà inscrit à ce cours");
        }
        List<FitnessClass> sameDay = activeClassesOf(me.id()).stream()
                .filter(o -> day(o).equals(day(c))).toList();
        if (sameDay.size() >= DAILY_LIMIT) {
            throw ApiException.conflict("DAILY_LIMIT_REACHED", DAILY_LIMIT + " cours maximum par jour");
        }
        if (sameDay.stream().anyMatch(o -> o.getStartsAt().isBefore(c.endsAt()) && o.endsAt().isAfter(c.getStartsAt()))) {
            throw ApiException.conflict("OVERLAPPING_CLASS", "Vous êtes déjà inscrit à un cours sur ce créneau");
        }
        long confirmed = bookings.countByClassIdAndStatus(id, FitnessBookingStatus.CONFIRMED);
        boolean full = confirmed >= c.getCapacity();
        if (!full && creditsLeft(me.id(), YearMonth.from(c.getStartsAt().atZone(ZONE))) <= 0) {
            throw ApiException.conflict("NO_CREDIT_LEFT", "Plus aucun crédit pour ce mois");
        }
        FitnessBooking b = existing.orElseGet(FitnessBooking::new);
        b.setClassId(id);
        b.setUserId(me.id());
        b.setStatus(full ? FitnessBookingStatus.WAITLISTED : FitnessBookingStatus.CONFIRMED);
        b.setDebited(!full);
        b.setUpdatedAt(clock.instant());
        bookings.saveAndFlush(b);
        Integer position = full ? waitPosition(bookings.findByClassIdOrderByCreatedAtAsc(id), b) : null;
        return new BookingResult(b.getStatus(), position, creditsLeft(me.id(), YearMonth.from(c.getStartsAt().atZone(ZONE))));
    }

    /** Annulation : gratuite jusqu'à 2 h avant (crédit rendu), sinon crédit perdu ; la place promeut le suivant. */
    @Transactional
    public ClassDto cancelBooking(AuthUser me, UUID id) {
        FitnessClass c = classes.lock(id).orElseThrow(() -> ApiException.notFound("Cours"));
        FitnessBooking b = bookings.findByClassIdAndUserId(id, me.id()).filter(x -> ACTIVE.contains(x.getStatus()))
                .orElseThrow(() -> ApiException.notFound("Réservation"));
        if (!c.getStartsAt().isAfter(clock.instant())) {
            throw ApiException.conflict("CLASS_STARTED", "Le cours a déjà commencé");
        }
        boolean wasConfirmed = b.getStatus() == FitnessBookingStatus.CONFIRMED;
        boolean free = clock.instant().isBefore(c.getStartsAt().minus(FREE_CANCEL));
        if (!wasConfirmed || free) {
            b.setStatus(FitnessBookingStatus.CANCELLED);
            b.setDebited(false);
        } else {
            b.setStatus(FitnessBookingStatus.LATE_CANCELLED);
        }
        b.setUpdatedAt(clock.instant());
        bookings.saveAndFlush(b);
        if (wasConfirmed) {
            promote(c);
        }
        return get(me, id);
    }

    /** Premier en attente qui a encore un crédit : confirmé et débité dans la même transaction. */
    private void promote(FitnessClass c) {
        YearMonth month = YearMonth.from(c.getStartsAt().atZone(ZONE));
        // rang d'attente = date de (ré)inscription (updated_at)
        for (FitnessBooking w : bookings.findByClassIdOrderByCreatedAtAsc(c.getId()).stream()
                .sorted(Comparator.comparing(FitnessBooking::getUpdatedAt)).toList()) {
            if (w.getStatus() != FitnessBookingStatus.WAITLISTED) {
                continue;
            }
            if (creditsLeft(w.getUserId(), month) > 0) {
                w.setStatus(FitnessBookingStatus.CONFIRMED);
                w.setDebited(true);
                w.setUpdatedAt(clock.instant());
                bookings.saveAndFlush(w);
                return;
            }
        }
    }

    // ---------- moi, coach, stats ----------

    @Transactional(readOnly = true)
    public MeDto me(AuthUser me) {
        YearMonth month = YearMonth.now(clock.withZone(ZONE));
        List<FitnessBooking> mine = bookings.findByUserIdOrderByCreatedAtDesc(me.id());
        Map<UUID, FitnessClass> byId = classes.findByIdIn(mine.stream().map(FitnessBooking::getClassId).toList()).stream()
                .collect(Collectors.toMap(FitnessClass::getId, Function.identity()));
        Instant since = month.atDay(1).atStartOfDay(ZONE).toInstant();
        List<MyBookingDto> list = mine.stream().filter(b -> !byId.get(b.getClassId()).getStartsAt().isBefore(since))
                .sorted(Comparator.comparing(b -> byId.get(b.getClassId()).getStartsAt()))
                .map(b -> {
                    FitnessClass c = byId.get(b.getClassId());
                    Integer pos = b.getStatus() == FitnessBookingStatus.WAITLISTED ? waitPosition(bookings.findByClassIdOrderByCreatedAtAsc(c.getId()), b) : null;
                    return new MyBookingDto(c.getId(), c.getType(), userLookup.name(c.getCoachId()), c.getStartsAt(), c.getDurationMinutes(), b.getStatus(), pos, b.isDebited());
                }).toList();
        int used = CREDITS_PER_MONTH - creditsLeft(me.id(), month);
        return new MeDto(month.toString(), CREDITS_PER_MONTH, used, CREDITS_PER_MONTH - used, list);
    }

    @Transactional(readOnly = true)
    public List<ClassSummary> coachClasses(AuthUser me) {
        roles.require(ROLES, me, COACH);
        return classes.findByCoachIdAndStartsAtAfterOrderByStartsAtAsc(me.id(), clock.instant().minus(Duration.ofDays(30)))
                .stream().map(c -> summary(me, c)).toList();
    }

    @Transactional(readOnly = true)
    public StatsDto stats(AuthUser me, YearMonth month) {
        me.requireAdmin();
        YearMonth m = month == null ? YearMonth.now(clock.withZone(ZONE)) : month;
        List<FitnessClass> cs = classes.findByStartsAtBetweenOrderByStartsAtAsc(m.atDay(1).atStartOfDay(ZONE).toInstant(),
                m.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant()).stream().filter(c -> !c.isCancelled()).toList();
        Map<UUID, Long> confirmed = cs.isEmpty() ? Map.of() : bookings.findByClassIdIn(cs.stream().map(FitnessClass::getId).toList()).stream()
                .filter(b -> b.getStatus() == FitnessBookingStatus.CONFIRMED || b.getStatus() == FitnessBookingStatus.LATE_CANCELLED)
                .collect(Collectors.groupingBy(FitnessBooking::getClassId, Collectors.counting()));
        return new StatsDto(m.toString(), rows(cs, FitnessClass::getType, confirmed),
                rows(cs, c -> userLookup.name(c.getCoachId()), confirmed));
    }

    private List<FillRow> rows(List<FitnessClass> cs, Function<FitnessClass, String> key, Map<UUID, Long> confirmed) {
        return cs.stream().collect(Collectors.groupingBy(key, TreeMap::new, Collectors.toList())).entrySet().stream().map(e -> {
            long cap = e.getValue().stream().mapToLong(FitnessClass::getCapacity).sum();
            long conf = e.getValue().stream().mapToLong(c -> confirmed.getOrDefault(c.getId(), 0L)).sum();
            return new FillRow(e.getKey(), e.getValue().size(), cap, conf, cap == 0 ? 0 : (int) Math.round(conf * 100.0 / cap));
        }).toList();
    }

    // ---------- règles ----------

    int creditsLeft(UUID userId, YearMonth month) {
        List<FitnessBooking> debited = bookings.findByUserIdAndDebitedTrue(userId);
        Map<UUID, FitnessClass> byId = classes.findByIdIn(debited.stream().map(FitnessBooking::getClassId).toList()).stream()
                .collect(Collectors.toMap(FitnessClass::getId, Function.identity()));
        long used = debited.stream().filter(b -> YearMonth.from(byId.get(b.getClassId()).getStartsAt().atZone(ZONE)).equals(month)).count();
        return (int) (CREDITS_PER_MONTH - used);
    }

    private List<FitnessClass> activeClassesOf(UUID userId) {
        List<FitnessBooking> active = bookings.findByUserIdAndStatusIn(userId, ACTIVE);
        return classes.findByIdIn(active.stream().map(FitnessBooking::getClassId).toList()).stream().filter(c -> !c.isCancelled()).toList();
    }

    private LocalDate day(FitnessClass c) {
        return c.getStartsAt().atZone(ZONE).toLocalDate();
    }

    private int waitPosition(List<FitnessBooking> bs, FitnessBooking b) {
        int pos = 0;
        for (FitnessBooking x : bs.stream().filter(x -> x.getStatus() == FitnessBookingStatus.WAITLISTED)
                .sorted(Comparator.comparing(FitnessBooking::getUpdatedAt)).toList()) {
            pos++;
            if (x.getId().equals(b.getId())) {
                return pos;
            }
        }
        return 0;
    }

    private FitnessClass find(UUID id) {
        return classes.findById(id).orElseThrow(() -> ApiException.notFound("Cours"));
    }

    private ClassSummary summary(AuthUser me, FitnessClass c) {
        List<FitnessBooking> bs = bookings.findByClassIdOrderByCreatedAtAsc(c.getId());
        long booked = bs.stream().filter(b -> b.getStatus() == FitnessBookingStatus.CONFIRMED).count();
        long waiting = bs.stream().filter(b -> b.getStatus() == FitnessBookingStatus.WAITLISTED).count();
        FitnessBookingStatus mine = bs.stream().filter(b -> b.getUserId().equals(me.id())).map(FitnessBooking::getStatus).findFirst().orElse(null);
        return new ClassSummary(c.getId(), c.getType(), c.getCoachId(), userLookup.name(c.getCoachId()), c.getStartsAt(), c.getDurationMinutes(),
                c.getCapacity(), booked, waiting, c.isCancelled(), mine);
    }
}
