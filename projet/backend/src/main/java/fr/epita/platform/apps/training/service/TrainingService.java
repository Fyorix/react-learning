package fr.epita.platform.apps.training.service;

import fr.epita.platform.apps.training.dto.TrainingDtos.AttendeeDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.CertificateDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.CourseDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.CourseRequest;
import fr.epita.platform.apps.training.dto.TrainingDtos.EnrollmentDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.FillDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.HoursDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.SessionDetailDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.SessionDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.SessionRequest;
import fr.epita.platform.apps.training.dto.TrainingDtos.StatsDto;
import fr.epita.platform.apps.training.entity.Course;
import fr.epita.platform.apps.training.entity.Enrollment;
import fr.epita.platform.apps.training.entity.EnrollmentStatus;
import fr.epita.platform.apps.training.entity.TrainingSession;
import fr.epita.platform.apps.training.repository.CourseRepository;
import fr.epita.platform.apps.training.repository.EnrollmentRepository;
import fr.epita.platform.apps.training.repository.TrainingSessionRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TrainingService {

    public static final String REPORTS = "training_reports";
    static final Duration CANCEL_DEADLINE = Duration.ofHours(48);

    private final CourseRepository courses;
    private final TrainingSessionRepository sessions;
    private final EnrollmentRepository enrollments;
    private final ManagerRelations relations;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- catalogue ----------

    @Transactional(readOnly = true)
    public List<CourseDto> catalog(AuthUser me, String theme, String search) {
        String term = search == null ? "" : search.trim().toLowerCase();
        return courses.findAllByOrderByTitleAsc().stream()
                .filter(c -> theme == null || theme.isBlank() || c.getTheme().equalsIgnoreCase(theme))
                .filter(c -> term.isEmpty() || c.getTitle().toLowerCase().contains(term) || c.getDescription().toLowerCase().contains(term))
                .map(c -> toDto(c, me, true)).toList();
    }

    @Transactional
    public CourseDto createCourse(AuthUser me, CourseRequest req) {
        me.requireAdmin();
        userLookup.require(req.trainerId());
        Course c = new Course();
        c.setTitle(req.title().trim());
        c.setTheme(req.theme().trim());
        c.setTrainerId(req.trainerId());
        c.setDurationHours(req.durationHours());
        c.setDescription(req.description().trim());
        return toDto(courses.saveAndFlush(c), me, true);
    }

    @Transactional
    public CourseDto addSession(AuthUser me, UUID courseId, SessionRequest req) {
        me.requireAdmin();
        Course c = course(courseId);
        var errors = new ValidationException.Collector();
        errors.check(req.start().isAfter(clock.instant()), "start", "doit être dans le futur");
        errors.check(req.end().isAfter(req.start()), "end", "doit être postérieure au début");
        errors.throwIfAny();
        TrainingSession s = new TrainingSession();
        s.setCourseId(courseId);
        s.setStartsAt(req.start());
        s.setEndsAt(req.end());
        s.setLocation(req.location().trim());
        s.setSeats(req.seats());
        sessions.saveAndFlush(s);
        return toDto(c, me, true);
    }

    @Transactional(readOnly = true)
    public SessionDetailDto session(AuthUser me, UUID id) {
        TrainingSession s = sessions.findById(id).orElseThrow(() -> ApiException.notFound("Session"));
        Course c = course(s.getCourseId());
        boolean staff = me.isAdmin() || c.getTrainerId().equals(me.id());
        List<AttendeeDto> attendees = !staff ? List.of() : enrollments.findBySessionId(id).stream()
                .filter(e -> e.getStatus().active())
                .map(e -> new AttendeeDto(e.getId(), e.getUserId(), userLookup.name(e.getUserId()), e.getStatus(),
                        waitlistPosition(e), e.getPresent()))
                .sorted(Comparator.comparing(AttendeeDto::status).thenComparing(AttendeeDto::userName)).toList();
        return new SessionDetailDto(toDto(s, me), c.getTitle(), userLookup.name(c.getTrainerId()), attendees);
    }

    // ---------- inscriptions ----------

    @Transactional
    public EnrollmentDto enroll(AuthUser me, UUID sessionId) {
        TrainingSession s = sessions.lock(sessionId).orElseThrow(() -> ApiException.notFound("Session"));
        if (!s.getStartsAt().isAfter(clock.instant())) {
            throw ApiException.conflict("SESSION_CLOSED", "Cette session a déjà commencé");
        }
        List<Enrollment> mine = enrollments.findByUserId(me.id());
        if (mine.stream().anyMatch(e -> e.getSessionId().equals(sessionId) && e.getStatus().active())) {
            throw ApiException.conflict("ALREADY_ENROLLED", "Vous êtes déjà inscrit à cette session");
        }
        for (Enrollment e : mine) {
            if (!e.getStatus().active()) {
                continue;
            }
            TrainingSession other = sessions.findById(e.getSessionId()).orElseThrow();
            if (other.getStartsAt().isBefore(s.getEndsAt()) && other.getEndsAt().isAfter(s.getStartsAt())) {
                throw ApiException.conflict("SESSION_OVERLAP", "Vous êtes inscrit à une autre session sur ce créneau");
            }
        }
        Enrollment e = new Enrollment();
        e.setSessionId(sessionId);
        e.setUserId(me.id());
        return toDto(enrollments.saveAndFlush(e), me);
    }

    /** Désinscription jusqu'à 48 h avant ; une place libérée confirme le premier de la liste d'attente. */
    @Transactional
    public EnrollmentDto cancel(AuthUser me, UUID sessionId) {
        TrainingSession s = sessions.lock(sessionId).orElseThrow(() -> ApiException.notFound("Session"));
        Enrollment e = enrollments.findBySessionId(sessionId).stream()
                .filter(x -> x.getUserId().equals(me.id()) && x.getStatus().active()).findFirst()
                .orElseThrow(() -> ApiException.notFound("Inscription"));
        if (clock.instant().plus(CANCEL_DEADLINE).isAfter(s.getStartsAt())) {
            throw ApiException.conflict("TOO_LATE_TO_CANCEL", "Désinscription impossible moins de 48 h avant la session");
        }
        boolean freed = e.getStatus() == EnrollmentStatus.CONFIRMED;
        e.setStatus(EnrollmentStatus.CANCELLED);
        enrollments.saveAndFlush(e);
        if (freed) {
            promote(s);
        }
        return toDto(e, me);
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDto> mine(AuthUser me) {
        return enrollments.findByUserId(me.id()).stream().map(e -> toDto(e, me))
                .sorted(Comparator.comparing(EnrollmentDto::start).reversed()).toList();
    }

    @Transactional(readOnly = true)
    public List<EnrollmentDto> team(AuthUser me, EnrollmentStatus status) {
        List<Enrollment> list = me.isAdmin() ? enrollments.findAll()
                : enrollments.findByUserIdIn(relations.employeesOf(REPORTS, me.id()));
        return list.stream().filter(e -> !e.getUserId().equals(me.id()))
                .filter(e -> status == null || e.getStatus() == status)
                .map(e -> toDto(e, me)).sorted(Comparator.comparing(EnrollmentDto::start)).toList();
    }

    /** Accord du manager : CONFIRMED s'il reste une place, sinon WAITLISTED (ordre d'accord). */
    @Transactional
    public EnrollmentDto approve(AuthUser me, UUID enrollmentId) {
        Enrollment e = decidable(me, enrollmentId);
        TrainingSession s = sessions.lock(e.getSessionId()).orElseThrow();
        e.setApprovedAt(clock.instant());
        e.setStatus(confirmedCount(s.getId()) < s.getSeats() ? EnrollmentStatus.CONFIRMED : EnrollmentStatus.WAITLISTED);
        return toDto(enrollments.saveAndFlush(e), me);
    }

    @Transactional
    public EnrollmentDto reject(AuthUser me, UUID enrollmentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ValidationException.field("reason", "ne doit pas être vide");
        }
        Enrollment e = decidable(me, enrollmentId);
        e.setStatus(EnrollmentStatus.REJECTED);
        e.setReason(reason.trim());
        return toDto(e, me);
    }

    @Transactional
    public SessionDetailDto attendance(AuthUser me, UUID sessionId, List<UUID> present) {
        TrainingSession s = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Session"));
        Course c = course(s.getCourseId());
        if (!me.isAdmin() && !c.getTrainerId().equals(me.id())) {
            throw ApiException.forbidden("Seul le formateur ou un ADMIN fait l'émargement");
        }
        if (s.getEndsAt().isAfter(clock.instant())) {
            throw ApiException.conflict("SESSION_NOT_DONE", "L'émargement se fait après la session");
        }
        Set<UUID> ids = new HashSet<>(present);
        for (Enrollment e : enrollments.findBySessionId(sessionId)) {
            if (e.getStatus() == EnrollmentStatus.CONFIRMED) {
                e.setPresent(ids.contains(e.getUserId()));
            }
        }
        enrollments.flush();
        return session(me, sessionId);
    }

    @Transactional(readOnly = true)
    public CertificateDto certificate(AuthUser me, UUID enrollmentId) {
        Enrollment e = enrollments.findById(enrollmentId).orElseThrow(() -> ApiException.notFound("Inscription"));
        if (!me.isAdmin() && !e.getUserId().equals(me.id())) {
            throw ApiException.notFound("Inscription");
        }
        if (!Boolean.TRUE.equals(e.getPresent())) {
            throw ApiException.conflict("NOT_ATTENDED", "L'attestation n'est délivrée qu'aux participants présents");
        }
        TrainingSession s = sessions.findById(e.getSessionId()).orElseThrow();
        Course c = course(s.getCourseId());
        return new CertificateDto(userLookup.name(e.getUserId()), c.getTitle(), userLookup.name(c.getTrainerId()),
                LocalDate.ofInstant(s.getStartsAt(), clock.getZone()), c.getDurationHours(),
                "ATT-" + e.getId().toString().substring(0, 8).toUpperCase());
    }

    @Transactional(readOnly = true)
    public StatsDto stats(AuthUser me, Integer year) {
        me.requireAdmin();
        int y = year == null ? LocalDate.now(clock).getYear() : year;
        ZoneId zone = clock.getZone();
        Map<UUID, Course> byCourse = new HashMap<>();
        courses.findAll().forEach(c -> byCourse.put(c.getId(), c));
        List<TrainingSession> ofYear = sessions.findAll().stream()
                .filter(s -> s.getStartsAt().atZone(zone).getYear() == y)
                .sorted(Comparator.comparing(TrainingSession::getStartsAt)).toList();
        List<FillDto> fill = ofYear.stream().map(s -> {
            long confirmed = confirmedCount(s.getId());
            return new FillDto(s.getId(), byCourse.get(s.getCourseId()).getTitle(), s.getStartsAt(), s.getSeats(), confirmed,
                    (int) Math.round(confirmed * 100.0 / s.getSeats()));
        }).toList();
        Map<UUID, BigDecimal> hours = new HashMap<>();
        for (TrainingSession s : ofYear) {
            for (Enrollment e : enrollments.findBySessionId(s.getId())) {
                if (Boolean.TRUE.equals(e.getPresent())) {
                    hours.merge(e.getUserId(), byCourse.get(s.getCourseId()).getDurationHours(), BigDecimal::add);
                }
            }
        }
        List<HoursDto> byEmployee = hours.entrySet().stream()
                .map(h -> new HoursDto(h.getKey(), userLookup.name(h.getKey()), h.getValue()))
                .sorted(Comparator.comparing(HoursDto::hours).reversed()).toList();
        return new StatsDto(y, fill, byEmployee);
    }

    // ---------- outils ----------

    private void promote(TrainingSession s) {
        enrollments.findBySessionId(s.getId()).stream().filter(x -> x.getStatus() == EnrollmentStatus.WAITLISTED)
                .min(Comparator.comparing(Enrollment::getApprovedAt))
                .ifPresent(first -> {
                    first.setStatus(EnrollmentStatus.CONFIRMED);
                    enrollments.saveAndFlush(first);
                });
    }

    private Enrollment decidable(AuthUser me, UUID id) {
        Enrollment e = enrollments.findById(id).orElseThrow(() -> ApiException.notFound("Inscription"));
        if (e.getUserId().equals(me.id())) {
            throw ApiException.forbidden("On ne valide pas sa propre inscription");
        }
        if (!me.isAdmin() && !relations.isManagerOf(REPORTS, me.id(), e.getUserId())) {
            throw ApiException.forbidden("Seul le manager du salarié valide son inscription");
        }
        if (e.getStatus() != EnrollmentStatus.REQUESTED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Cette inscription a déjà été traitée");
        }
        return e;
    }

    private long confirmedCount(UUID sessionId) {
        return enrollments.findBySessionId(sessionId).stream().filter(e -> e.getStatus() == EnrollmentStatus.CONFIRMED).count();
    }

    private Integer waitlistPosition(Enrollment e) {
        if (e.getStatus() != EnrollmentStatus.WAITLISTED) {
            return null;
        }
        List<Enrollment> queue = enrollments.findBySessionId(e.getSessionId()).stream()
                .filter(x -> x.getStatus() == EnrollmentStatus.WAITLISTED)
                .sorted(Comparator.comparing(Enrollment::getApprovedAt)).toList();
        return queue.indexOf(e) + 1;
    }

    private Course course(UUID id) {
        return courses.findById(id).orElseThrow(() -> ApiException.notFound("Formation"));
    }

    private CourseDto toDto(Course c, AuthUser me, boolean upcomingOnly) {
        Instant now = clock.instant();
        List<SessionDto> list = sessions.findByCourseIdOrderByStartsAtAsc(c.getId()).stream()
                .filter(s -> !upcomingOnly || s.getEndsAt().isAfter(now)).map(s -> toDto(s, me)).toList();
        return new CourseDto(c.getId(), c.getTitle(), c.getTheme(), c.getTrainerId(), userLookup.name(c.getTrainerId()),
                c.getDurationHours(), c.getDescription(), list);
    }

    private SessionDto toDto(TrainingSession s, AuthUser me) {
        List<Enrollment> list = enrollments.findBySessionId(s.getId());
        long confirmed = list.stream().filter(e -> e.getStatus() == EnrollmentStatus.CONFIRMED).count();
        long waitlisted = list.stream().filter(e -> e.getStatus() == EnrollmentStatus.WAITLISTED).count();
        Enrollment mine = list.stream().filter(e -> e.getUserId().equals(me.id()) && e.getStatus() != EnrollmentStatus.CANCELLED)
                .max(Comparator.comparing(Enrollment::getCreatedAt)).orElse(null);
        return new SessionDto(s.getId(), s.getCourseId(), s.getStartsAt(), s.getEndsAt(), s.getLocation(), s.getSeats(),
                confirmed, waitlisted, Math.max(0, s.getSeats() - confirmed), mine == null ? null : mine.getStatus(),
                mine == null ? null : waitlistPosition(mine), mine == null ? null : mine.getId(), !s.getEndsAt().isAfter(clock.instant()));
    }

    private EnrollmentDto toDto(Enrollment e, AuthUser me) {
        TrainingSession s = sessions.findById(e.getSessionId()).orElseThrow();
        Course c = course(s.getCourseId());
        boolean canCancel = e.getUserId().equals(me.id()) && e.getStatus().active()
                && !clock.instant().plus(CANCEL_DEADLINE).isAfter(s.getStartsAt());
        return new EnrollmentDto(e.getId(), s.getId(), e.getUserId(), userLookup.name(e.getUserId()), c.getTitle(),
                s.getStartsAt(), s.getEndsAt(), s.getLocation(), e.getStatus(), waitlistPosition(e), e.getReason(),
                e.getPresent(), canCancel);
    }
}
