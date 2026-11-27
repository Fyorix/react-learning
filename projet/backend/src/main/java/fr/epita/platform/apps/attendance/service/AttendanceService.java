package fr.epita.platform.apps.attendance.service;

import fr.epita.platform.apps.attendance.dto.AttendanceDtos.*;
import fr.epita.platform.apps.attendance.entity.*;
import fr.epita.platform.apps.attendance.repository.AttendanceEntryRepository;
import fr.epita.platform.apps.attendance.repository.AttendanceGroupRepository;
import fr.epita.platform.apps.attendance.repository.AttendanceSessionRepository;
import fr.epita.platform.apps.attendance.repository.JustificationRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.FileStorage;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Appel modifiable jusqu'à 24 h après la fin de la séance ; justificatif déposé au plus tard 7 jours après l'absence,
 * validé par un ADMIN. Compteurs par matière : 3 retards valent 1 absence non justifiée, alerte au-delà de 3.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    public static final String ROLES = "attendance_roles";
    public static final String TEACHER = "TEACHER";
    static final Duration ROLL_CALL_WINDOW = Duration.ofHours(24);
    static final Duration JUSTIFY_WINDOW = Duration.ofDays(7);
    static final int LATES_PER_ABSENCE = 3;
    static final int ALERT_THRESHOLD = 3;
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final AttendanceGroupRepository groups;
    private final AttendanceSessionRepository sessions;
    private final AttendanceEntryRepository entries;
    private final JustificationRepository justifications;
    private final FileStorage files;
    private final AppRoles roles;
    private final UserLookup userLookup;
    private final Clock clock;

    public record JustificationFile(byte[] content, String contentType, String fileName) {
    }

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(TEACHER));
    }

    // ---------- référentiel (ADMIN) ----------

    @Transactional(readOnly = true)
    public List<GroupDto> groups(AuthUser me) {
        staff(me);
        return groups.findAllByOrderByNameAsc().stream().map(g -> new GroupDto(g.getId(), g.getName(), g.getStudentIds().size())).toList();
    }

    @Transactional
    public GroupDto createGroup(AuthUser me, GroupRequest req) {
        me.requireAdmin();
        if (groups.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("GROUP_EXISTS", "Un groupe porte déjà ce nom");
        }
        for (int i = 0; i < req.studentIds().size(); i++) {
            UUID s = req.studentIds().get(i);
            userLookup.require(s);
            if (groups.ofStudent(s).isPresent()) {
                throw ValidationException.field("studentIds[" + i + "]", userLookup.name(s) + " est déjà dans un groupe");
            }
        }
        AttendanceGroup g = new AttendanceGroup();
        g.setName(req.name().trim());
        g.setStudentIds(new HashSet<>(req.studentIds()));
        groups.saveAndFlush(g);
        return new GroupDto(g.getId(), g.getName(), g.getStudentIds().size());
    }

    @Transactional
    public List<SessionDto> createSessions(AuthUser me, SessionRequest req) {
        me.requireAdmin();
        group(req.groupId());
        if (!req.end().isAfter(req.start()) || Duration.between(req.start(), req.end()).compareTo(Duration.ofHours(8)) > 0) {
            throw ValidationException.field("end", "postérieure au début, 8 h maximum");
        }
        if (!roles.hasExplicit(ROLES, req.teacherId(), TEACHER)) {
            throw ValidationException.field("teacherId", "l'enseignant doit avoir le rôle TEACHER");
        }
        int weeks = req.repeatWeeks() == null ? 1 : req.repeatWeeks();
        List<SessionDto> out = new ArrayList<>();
        for (int w = 0; w < weeks; w++) {
            AttendanceSession s = new AttendanceSession();
            s.setSubject(req.subject().trim());
            s.setGroupId(req.groupId());
            s.setTeacherId(req.teacherId());
            // même heure locale d'une semaine à l'autre (changement d'heure compris)
            s.setStartAt(req.start().atZone(ZONE).plusWeeks(w).toInstant());
            s.setEndAt(req.end().atZone(ZONE).plusWeeks(w).toInstant());
            out.add(toDto(sessions.saveAndFlush(s)));
        }
        return out;
    }

    // ---------- appel ----------

    @Transactional(readOnly = true)
    public List<SessionDto> teacherSessions(AuthUser me, LocalDate date) {
        roles.require(ROLES, me, TEACHER);
        List<AttendanceSession> list = date == null ? sessions.findByTeacherIdOrderByStartAtDesc(me.id())
                : sessions.findByTeacherIdAndStartAtBetweenOrderByStartAtAsc(me.id(), date.atStartOfDay(ZONE).toInstant(), date.plusDays(1).atStartOfDay(ZONE).toInstant());
        return list.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SessionDetailDto session(AuthUser me, UUID id) {
        AttendanceSession s = sessions.findById(id).orElseThrow(() -> ApiException.notFound("Séance"));
        requireTeacherOf(me, s);
        return detail(s);
    }

    @Transactional
    public SessionDetailDto rollCall(AuthUser me, UUID id, List<RollCallEntry> input) {
        AttendanceSession s = sessions.lock(id).orElseThrow(() -> ApiException.notFound("Séance"));
        requireTeacherOf(me, s);
        Instant now = clock.instant();
        if (now.isBefore(s.getStartAt())) {
            throw ApiException.conflict("SESSION_NOT_STARTED", "L'appel se fait à partir du début de la séance");
        }
        if (now.isAfter(s.getEndAt().plus(ROLL_CALL_WINDOW))) {
            throw ApiException.conflict("ATTENDANCE_LOCKED", "L'appel n'est plus modifiable 24 h après la fin de la séance");
        }
        Set<UUID> students = group(s.getGroupId()).getStudentIds();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < input.size(); i++) {
            UUID st = input.get(i).studentId();
            if (!students.contains(st) || !seen.add(st)) {
                throw ValidationException.field("entries[" + i + "].studentId", "étudiant hors du groupe ou en double");
            }
        }
        for (RollCallEntry e : input) {
            AttendanceEntry entry = entries.findBySessionIdAndStudentId(s.getId(), e.studentId())
                    .orElseGet(() -> new AttendanceEntry(UUID.randomUUID(), s.getId(), e.studentId(), e.status()));
            entry.setStatus(e.status());
            entries.save(entry);
        }
        entries.flush();
        return detail(s);
    }

    // ---------- étudiant ----------

    @Transactional(readOnly = true)
    public MyAttendanceDto mine(AuthUser me) {
        List<AttendanceEntry> mine = entries.findByStudentId(me.id()).stream().filter(e -> e.getStatus() != PresenceStatus.PRESENT).toList();
        Map<UUID, AttendanceSession> byId = sessions.findByIdIn(mine.stream().map(AttendanceEntry::getSessionId).toList()).stream()
                .collect(Collectors.toMap(AttendanceSession::getId, s -> s));
        Map<UUID, JustificationStatus> state = justificationState(me.id());
        List<AbsenceDto> absences = mine.stream().map(e -> toDto(e, byId.get(e.getSessionId()), state.get(e.getId())))
                .sorted(Comparator.comparing(AbsenceDto::start).reversed()).toList();
        return new MyAttendanceDto(absences, counters(absences));
    }

    @Transactional
    public JustificationDto justify(AuthUser me, JustificationRequest req, MultipartFile file) {
        Instant now = clock.instant();
        Map<UUID, JustificationStatus> state = justificationState(me.id());
        Set<UUID> ids = new LinkedHashSet<>(req.absenceIds());
        int i = 0;
        for (UUID id : ids) {
            AttendanceEntry e = entries.findById(id).filter(x -> x.getStudentId().equals(me.id())).orElseThrow(() -> ApiException.notFound("Absence"));
            if (e.getStatus() != PresenceStatus.ABSENT) {
                throw ValidationException.field("absenceIds[" + i + "]", "seules les absences se justifient");
            }
            JustificationStatus current = state.get(id);
            if (current == JustificationStatus.PENDING || current == JustificationStatus.ACCEPTED) {
                throw ApiException.conflict("ALREADY_JUSTIFIED", "Un justificatif couvre déjà cette absence");
            }
            AttendanceSession s = sessions.findById(e.getSessionId()).orElseThrow();
            if (now.isAfter(s.getEndAt().plus(JUSTIFY_WINDOW))) {
                throw ApiException.conflict("JUSTIFICATION_TOO_LATE", "Le justificatif devait être déposé dans les 7 jours suivant l'absence du " + s.getStartAt().atZone(ZONE).toLocalDate());
            }
            i++;
        }
        FileStorage.Stored stored = files.store(file, FileStorage.DOCUMENTS);
        Justification j = new Justification();
        j.setStudentId(me.id());
        j.setReason(req.reason().trim());
        j.setStatus(JustificationStatus.PENDING);
        j.setFileKey(stored.key());
        j.setContentType(stored.contentType());
        j.setFileName(stored.originalName());
        j.setEntryIds(ids);
        return toDto(justifications.saveAndFlush(j));
    }

    @Transactional(readOnly = true)
    public List<JustificationDto> list(AuthUser me, JustificationStatus status) {
        if (me.isAdmin()) {
            return (status == null ? justifications.findAllByOrderByCreatedAtDesc() : justifications.findByStatusOrderByCreatedAtAsc(status)).stream().map(this::toDto).toList();
        }
        return justifications.findByStudentIdOrderByCreatedAtDesc(me.id()).stream().filter(j -> status == null || j.getStatus() == status).map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public JustificationFile file(AuthUser me, UUID id) {
        Justification j = justifications.findById(id).orElseThrow(() -> ApiException.notFound("Justificatif"));
        if (!me.isAdmin() && !j.getStudentId().equals(me.id())) {
            throw ApiException.notFound("Justificatif");
        }
        return new JustificationFile(files.load(j.getFileKey()), j.getContentType(), j.getFileName());
    }

    @Transactional
    public JustificationDto decide(AuthUser me, UUID id, boolean accept, String reason) {
        me.requireAdmin();
        Justification j = justifications.lock(id).orElseThrow(() -> ApiException.notFound("Justificatif"));
        if (j.getStatus() != JustificationStatus.PENDING) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Justificatif déjà " + j.getStatus());
        }
        j.setStatus(accept ? JustificationStatus.ACCEPTED : JustificationStatus.REJECTED);
        j.setAdminReason(accept ? null : reason.trim());
        j.setDecidedBy(me.id());
        return toDto(justifications.saveAndFlush(j));
    }

    /** Étudiants au-delà de 3 absences non justifiées (retards compris, 3 pour 1) dans une matière. */
    @Transactional(readOnly = true)
    public List<AlertDto> alerts(AuthUser me) {
        me.requireAdmin();
        List<AlertDto> out = new ArrayList<>();
        for (AttendanceGroup g : groups.findAllByOrderByNameAsc()) {
            for (UUID student : g.getStudentIds()) {
                List<AttendanceEntry> mine = entries.findByStudentId(student).stream().filter(e -> e.getStatus() != PresenceStatus.PRESENT).toList();
                Map<UUID, AttendanceSession> byId = sessions.findByIdIn(mine.stream().map(AttendanceEntry::getSessionId).toList()).stream()
                        .collect(Collectors.toMap(AttendanceSession::getId, s -> s));
                Map<UUID, JustificationStatus> state = justificationState(student);
                List<AbsenceDto> abs = mine.stream().map(e -> toDto(e, byId.get(e.getSessionId()), state.get(e.getId()))).toList();
                counters(abs).stream().filter(CounterDto::alert)
                        .forEach(c -> out.add(new AlertDto(student, userLookup.name(student), g.getName(), c.subject(), c.effectiveUnjustified(), c.late())));
            }
        }
        out.sort(Comparator.comparingInt(AlertDto::effectiveUnjustified).reversed().thenComparing(AlertDto::studentName, Comparator.nullsLast(String::compareTo)));
        return out;
    }

    // ---------- calculs ----------

    static List<CounterDto> counters(List<AbsenceDto> absences) {
        Map<String, List<AbsenceDto>> bySubject = absences.stream().collect(Collectors.groupingBy(AbsenceDto::subject, TreeMap::new, Collectors.toList()));
        return bySubject.entrySet().stream().map(e -> {
            int justified = (int) e.getValue().stream().filter(a -> a.status() == PresenceStatus.ABSENT && a.justification() == JustificationStatus.ACCEPTED).count();
            int unjustified = (int) e.getValue().stream().filter(a -> a.status() == PresenceStatus.ABSENT && a.justification() != JustificationStatus.ACCEPTED).count();
            int late = (int) e.getValue().stream().filter(a -> a.status() == PresenceStatus.LATE).count();
            int effective = unjustified + late / LATES_PER_ABSENCE;
            return new CounterDto(e.getKey(), justified, unjustified, late, effective, effective > ALERT_THRESHOLD);
        }).toList();
    }

    /** Statut du justificatif le plus favorable pour chaque absence : ACCEPTED, sinon PENDING, sinon REJECTED. */
    private Map<UUID, JustificationStatus> justificationState(UUID studentId) {
        Map<UUID, JustificationStatus> out = new HashMap<>();
        for (Justification j : justifications.findByStudentIdOrderByCreatedAtDesc(studentId)) {
            for (UUID e : j.getEntryIds()) {
                out.merge(e, j.getStatus(), (a, b) -> rank(a) >= rank(b) ? a : b);
            }
        }
        return out;
    }

    private static int rank(JustificationStatus s) {
        return switch (s) {
            case ACCEPTED -> 3;
            case PENDING -> 2;
            case REJECTED -> 1;
        };
    }

    // ---------- accès ----------

    private void staff(AuthUser me) {
        if (!me.isAdmin() && !roles.hasExplicit(ROLES, me.id(), TEACHER)) {
            throw ApiException.forbidden("Réservé aux enseignants et aux ADMIN");
        }
    }

    private void requireTeacherOf(AuthUser me, AttendanceSession s) {
        if (!me.isAdmin() && !s.getTeacherId().equals(me.id())) {
            throw ApiException.forbidden("Réservé à l'enseignant de la séance");
        }
    }

    private AttendanceGroup group(UUID id) {
        return groups.findById(id).orElseThrow(() -> ApiException.notFound("Groupe"));
    }

    // ---------- conversions ----------

    private SessionDto toDto(AttendanceSession s) {
        AttendanceGroup g = group(s.getGroupId());
        Instant lockedAt = s.getEndAt().plus(ROLL_CALL_WINDOW);
        Instant now = clock.instant();
        return new SessionDto(s.getId(), s.getSubject(), g.getId(), g.getName(), s.getTeacherId(), userLookup.name(s.getTeacherId()), s.getStartAt(), s.getEndAt(),
                entries.findBySessionId(s.getId()).size(), g.getStudentIds().size(), !now.isBefore(s.getStartAt()) && !now.isAfter(lockedAt), lockedAt);
    }

    private SessionDetailDto detail(AttendanceSession s) {
        Map<UUID, PresenceStatus> byStudent = entries.findBySessionId(s.getId()).stream().collect(Collectors.toMap(AttendanceEntry::getStudentId, AttendanceEntry::getStatus));
        Set<UUID> students = group(s.getGroupId()).getStudentIds();
        Map<UUID, String> names = userLookup.names(students);
        List<RollCallLine> lines = students.stream().map(u -> new RollCallLine(u, names.get(u), byStudent.get(u)))
                .sorted(Comparator.comparing(RollCallLine::name, Comparator.nullsLast(String::compareTo))).toList();
        return new SessionDetailDto(toDto(s), lines);
    }

    private AbsenceDto toDto(AttendanceEntry e, AttendanceSession s, JustificationStatus j) {
        Instant before = s.getEndAt().plus(JUSTIFY_WINDOW);
        boolean justifiable = e.getStatus() == PresenceStatus.ABSENT && (j == null || j == JustificationStatus.REJECTED) && !clock.instant().isAfter(before);
        return new AbsenceDto(e.getId(), s.getId(), s.getSubject(), s.getStartAt(), s.getEndAt(), e.getStatus(), j, before, justifiable);
    }

    private JustificationDto toDto(Justification j) {
        List<AttendanceEntry> es = entries.findAllById(j.getEntryIds());
        Map<UUID, AttendanceSession> byId = sessions.findByIdIn(es.stream().map(AttendanceEntry::getSessionId).toList()).stream()
                .collect(Collectors.toMap(AttendanceSession::getId, s -> s));
        List<AbsenceDto> abs = es.stream().map(e -> toDto(e, byId.get(e.getSessionId()), j.getStatus())).sorted(Comparator.comparing(AbsenceDto::start)).toList();
        return new JustificationDto(j.getId(), j.getStudentId(), userLookup.name(j.getStudentId()), j.getReason(), j.getStatus(), j.getFileName(),
                j.getAdminReason(), j.getDecidedBy() == null ? null : userLookup.name(j.getDecidedBy()), abs, j.getCreatedAt());
    }
}
