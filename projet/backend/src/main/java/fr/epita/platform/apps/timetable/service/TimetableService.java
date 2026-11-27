package fr.epita.platform.apps.timetable.service;

import fr.epita.platform.apps.timetable.dto.TimetableDtos.*;
import fr.epita.platform.apps.timetable.entity.TimetableCourse;
import fr.epita.platform.apps.timetable.entity.TimetableGroup;
import fr.epita.platform.apps.timetable.entity.TimetableRoom;
import fr.epita.platform.apps.timetable.entity.TimetableSubject;
import fr.epita.platform.apps.timetable.repository.TimetableCourseRepository;
import fr.epita.platform.apps.timetable.repository.TimetableGroupRepository;
import fr.epita.platform.apps.timetable.repository.TimetableRoomRepository;
import fr.epita.platform.apps.timetable.repository.TimetableSubjectRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.IsoWeeks;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

/**
 * Placement des cours sans conflit de salle, d'enseignant ni de groupe, dans la capacité de la salle et le volume
 * prévu de la matière. Les placements sont sérialisés (verrou consultatif PostgreSQL) pour qu'aucun conflit
 * ne passe entre deux requêtes simultanées.
 */
@Service
@RequiredArgsConstructor
public class TimetableService {

    public static final String ROLES = "timetable_roles";
    public static final String TEACHER = "TEACHER";
    static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    static final LocalTime DAY_START = LocalTime.of(8, 0);
    static final LocalTime DAY_END = LocalTime.of(20, 0);

    private final TimetableRoomRepository rooms;
    private final TimetableGroupRepository groups;
    private final TimetableSubjectRepository subjects;
    private final TimetableCourseRepository courses;
    private final AppRoles roles;
    private final UserLookup userLookup;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(TEACHER));
    }

    // ---------- référentiel ----------

    @Transactional(readOnly = true)
    public List<RoomDto> rooms() {
        return rooms.findAllByOrderByNameAsc().stream().map(r -> new RoomDto(r.getId(), r.getName(), r.getCapacity())).toList();
    }

    @Transactional
    public RoomDto createRoom(AuthUser me, RoomRequest req) {
        me.requireAdmin();
        if (rooms.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("ROOM_EXISTS", "Une salle porte déjà ce nom");
        }
        TimetableRoom r = new TimetableRoom();
        r.setName(req.name().trim());
        r.setCapacity(req.capacity());
        rooms.saveAndFlush(r);
        return new RoomDto(r.getId(), r.getName(), r.getCapacity());
    }

    @Transactional(readOnly = true)
    public List<GroupDto> groups() {
        return groups.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public GroupDto createGroup(AuthUser me, GroupRequest req) {
        me.requireAdmin();
        if (groups.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("GROUP_EXISTS", "Un groupe porte déjà ce nom");
        }
        Set<UUID> students = new HashSet<>(req.studentIds());
        if (req.size() < students.size()) {
            throw ValidationException.field("size", "inférieur au nombre d'étudiants inscrits (" + students.size() + ")");
        }
        int i = 0;
        for (UUID s : req.studentIds()) {
            userLookup.require(s);
            if (groups.ofStudent(s).isPresent()) {
                throw ValidationException.field("studentIds[" + i + "]", userLookup.name(s) + " est déjà dans un groupe");
            }
            i++;
        }
        TimetableGroup g = new TimetableGroup();
        g.setName(req.name().trim());
        g.setSize(req.size());
        g.setStudentIds(students);
        return toDto(groups.saveAndFlush(g));
    }

    @Transactional(readOnly = true)
    public List<SubjectDto> subjects(UUID groupId) {
        List<TimetableSubject> list = groupId == null ? subjects.findAllByOrderByNameAsc() : subjects.findByGroupIdOrderByNameAsc(groupId);
        return list.stream().map(this::toDto).toList();
    }

    @Transactional
    public SubjectDto createSubject(AuthUser me, SubjectRequest req) {
        me.requireAdmin();
        group(req.groupId());
        if (subjects.existsByGroupIdAndNameIgnoreCase(req.groupId(), req.name().trim())) {
            throw ApiException.conflict("SUBJECT_EXISTS", "Cette matière existe déjà pour ce groupe");
        }
        TimetableSubject s = new TimetableSubject();
        s.setName(req.name().trim());
        s.setPlannedHours(req.plannedHours());
        s.setGroupId(req.groupId());
        return toDto(subjects.saveAndFlush(s));
    }

    // ---------- consultation ----------

    @Transactional(readOnly = true)
    public List<CourseDto> week(String week, UUID groupId, UUID teacherId, UUID roomId) {
        int filters = (groupId == null ? 0 : 1) + (teacherId == null ? 0 : 1) + (roomId == null ? 0 : 1);
        if (filters != 1) {
            throw ValidationException.field("groupId", "indiquer exactement un filtre : groupId, teacherId ou roomId");
        }
        return weekCourses(week).stream()
                .filter(c -> (groupId == null || c.getGroupId().equals(groupId)) && (teacherId == null || c.getTeacherId().equals(teacherId))
                        && (roomId == null || c.getRoomId().equals(roomId)))
                .map(this::toDto).toList();
    }

    /** Enseignant : ses cours ; étudiant : ceux de son groupe. */
    @Transactional(readOnly = true)
    public List<CourseDto> mine(AuthUser me, String week) {
        if (roles.hasExplicit(ROLES, me.id(), TEACHER)) {
            return weekCourses(week).stream().filter(c -> c.getTeacherId().equals(me.id())).map(this::toDto).toList();
        }
        Optional<TimetableGroup> g = groups.ofStudent(me.id());
        if (g.isEmpty()) {
            return List.of();
        }
        return weekCourses(week).stream().filter(c -> c.getGroupId().equals(g.get().getId())).map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<HoursDto> hours(UUID groupId) {
        group(groupId);
        Instant now = clock.instant();
        return subjects.findByGroupIdOrderByNameAsc(groupId).stream().map(s -> {
            List<TimetableCourse> cs = courses.findBySubjectId(s.getId());
            long scheduled = cs.stream().mapToLong(c -> minutes(c.getStartAt(), c.getEndAt())).sum();
            long done = cs.stream().filter(c -> !c.getEndAt().isAfter(now)).mapToLong(c -> minutes(c.getStartAt(), c.getEndAt())).sum();
            return new HoursDto(s.getId(), s.getName(), s.getPlannedHours(), scheduled / 60.0, done / 60.0);
        }).toList();
    }

    // ---------- placement ----------

    @Transactional(readOnly = true)
    public CheckDto check(AuthUser me, CourseRequest req, UUID excludeId) {
        me.requireAdmin();
        List<ConflictDto> conflicts = conflicts(req, excludeId);
        return new CheckDto(conflicts.isEmpty(), conflicts);
    }

    @Transactional
    public CourseDto create(AuthUser me, CourseRequest req) {
        me.requireAdmin();
        lockPlanning();
        TimetableCourse c = new TimetableCourse();
        apply(c, req);
        return toDto(courses.saveAndFlush(c));
    }

    @Transactional
    public CourseDto update(AuthUser me, UUID id, CourseRequest req) {
        me.requireAdmin();
        lockPlanning();
        TimetableCourse c = notPast(id);
        apply(c, req);
        return toDto(courses.saveAndFlush(c));
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        me.requireAdmin();
        lockPlanning();
        courses.delete(notPast(id));
    }

    private void apply(TimetableCourse c, CourseRequest req) {
        List<ConflictDto> conflicts = conflicts(req, c.getId());
        if (!conflicts.isEmpty()) {
            ConflictDto first = conflicts.get(0);
            throw ApiException.conflict(first.code(), first.message());
        }
        TimetableSubject s = subject(req.subjectId());
        c.setSubjectId(s.getId());
        c.setGroupId(s.getGroupId());
        c.setTeacherId(req.teacherId());
        c.setRoomId(req.roomId());
        c.setStartAt(req.start());
        c.setEndAt(req.end());
    }

    /** Toutes les violations d'un cours candidat : format (400), puis conflits métier (409), dans l'ordre. */
    List<ConflictDto> conflicts(CourseRequest req, UUID excludeId) {
        validateSlot(req.start(), req.end());
        TimetableSubject s = subject(req.subjectId());
        TimetableGroup g = group(s.getGroupId());
        TimetableRoom room = rooms.findById(req.roomId()).orElseThrow(() -> ApiException.notFound("Salle"));
        userLookup.require(req.teacherId());
        if (!roles.hasExplicit(ROLES, req.teacherId(), TEACHER)) {
            throw ValidationException.field("teacherId", "l'enseignant doit avoir le rôle TEACHER");
        }
        List<ConflictDto> out = new ArrayList<>();
        if (req.start().isBefore(clock.instant())) {
            out.add(new ConflictDto("COURSE_IN_PAST", "Le créneau est déjà passé"));
        }
        List<TimetableCourse> overlapping = courses.overlapping(req.start(), req.end()).stream().filter(o -> !o.getId().equals(excludeId)).toList();
        overlapping.stream().filter(o -> o.getRoomId().equals(room.getId())).findFirst()
                .ifPresent(o -> out.add(new ConflictDto("ROOM_CONFLICT", "La salle " + room.getName() + " est occupée par " + describe(o))));
        overlapping.stream().filter(o -> o.getTeacherId().equals(req.teacherId())).findFirst()
                .ifPresent(o -> out.add(new ConflictDto("TEACHER_CONFLICT", userLookup.name(req.teacherId()) + " enseigne déjà : " + describe(o))));
        overlapping.stream().filter(o -> o.getGroupId().equals(g.getId())).findFirst()
                .ifPresent(o -> out.add(new ConflictDto("GROUP_CONFLICT", "Le groupe " + g.getName() + " a déjà cours : " + describe(o))));
        if (room.getCapacity() < g.getSize()) {
            out.add(new ConflictDto("ROOM_TOO_SMALL", "La salle " + room.getName() + " (" + room.getCapacity() + " places) est trop petite pour " + g.getSize() + " étudiants"));
        }
        long planned = courses.findBySubjectId(s.getId()).stream().filter(o -> !o.getId().equals(excludeId)).mapToLong(o -> minutes(o.getStartAt(), o.getEndAt())).sum();
        if (planned + minutes(req.start(), req.end()) > s.getPlannedHours() * 60L) {
            out.add(new ConflictDto("HOURS_EXCEEDED", "Volume dépassé pour " + s.getName() + " : " + (planned / 60.0) + " h déjà planifiées sur " + s.getPlannedHours() + " h"));
        }
        return out;
    }

    /** De 8 h à 20 h (heure de Paris), le même jour, en quarts d'heure, 30 min à 4 h. */
    static void validateSlot(Instant start, Instant end) {
        ZonedDateTime s = start.atZone(ZONE);
        ZonedDateTime e = end.atZone(ZONE);
        long duration = minutes(start, end);
        var errors = new ValidationException.Collector();
        errors.check(s.getMinute() % 15 == 0 && s.getSecond() == 0 && s.getNano() == 0, "start", "en quarts d'heure (ex. 9:00, 9:15)");
        errors.check(e.getMinute() % 15 == 0 && e.getSecond() == 0 && e.getNano() == 0, "end", "en quarts d'heure (ex. 10:30)");
        errors.check(duration >= 30 && duration <= 240, "end", "un cours dure de 30 min à 4 h");
        errors.check(s.toLocalDate().equals(e.toLocalDate()), "end", "le cours se termine le jour où il commence");
        errors.check(!s.toLocalTime().isBefore(DAY_START), "start", "pas avant 8 h");
        errors.check(!e.toLocalTime().isAfter(DAY_END) && !e.toLocalTime().isBefore(DAY_START), "end", "pas après 20 h");
        errors.throwIfAny();
    }

    private TimetableCourse notPast(UUID id) {
        TimetableCourse c = courses.findById(id).orElseThrow(() -> ApiException.notFound("Cours"));
        if (!c.getStartAt().isAfter(clock.instant())) {
            throw ApiException.conflict("COURSE_IN_PAST", "Un cours passé ne peut plus être modifié ni supprimé");
        }
        return c;
    }

    private void lockPlanning() {
        jdbc.queryForList("select pg_advisory_xact_lock(hashtext('timetable_courses'))");
    }

    private List<TimetableCourse> weekCourses(String week) {
        LocalDate monday = IsoWeeks.monday(week == null ? IsoWeeks.of(LocalDate.now(clock.withZone(ZONE))) : week);
        Instant from = monday.atStartOfDay(ZONE).toInstant();
        Instant to = monday.plusDays(7).atStartOfDay(ZONE).toInstant();
        return courses.overlapping(from, to).stream().sorted(Comparator.comparing(TimetableCourse::getStartAt)).toList();
    }

    private static long minutes(Instant a, Instant b) {
        return Duration.between(a, b).toMinutes();
    }

    private String describe(TimetableCourse o) {
        return subject(o.getSubjectId()).getName() + " (" + o.getStartAt().atZone(ZONE).toLocalTime() + "–" + o.getEndAt().atZone(ZONE).toLocalTime() + ")";
    }

    private TimetableGroup group(UUID id) {
        return groups.findById(id).orElseThrow(() -> ApiException.notFound("Groupe"));
    }

    private TimetableSubject subject(UUID id) {
        return subjects.findById(id).orElseThrow(() -> ApiException.notFound("Matière"));
    }

    // ---------- conversions ----------

    private GroupDto toDto(TimetableGroup g) {
        return new GroupDto(g.getId(), g.getName(), g.getSize(), g.getStudentIds().size());
    }

    private SubjectDto toDto(TimetableSubject s) {
        return new SubjectDto(s.getId(), s.getName(), s.getPlannedHours(), s.getGroupId(), group(s.getGroupId()).getName());
    }

    private CourseDto toDto(TimetableCourse c) {
        TimetableRoom r = rooms.findById(c.getRoomId()).orElseThrow();
        return new CourseDto(c.getId(), c.getSubjectId(), subject(c.getSubjectId()).getName(), c.getGroupId(), group(c.getGroupId()).getName(),
                c.getTeacherId(), userLookup.name(c.getTeacherId()), r.getId(), r.getName(), c.getStartAt(), c.getEndAt(), !c.getStartAt().isAfter(clock.instant()));
    }
}
