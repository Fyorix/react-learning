package fr.epita.platform.apps.defense.service;

import fr.epita.platform.apps.defense.dto.DefenseDtos.*;
import fr.epita.platform.apps.defense.entity.DefenseGrade;
import fr.epita.platform.apps.defense.entity.DefenseGroup;
import fr.epita.platform.apps.defense.entity.DefenseSlot;
import fr.epita.platform.apps.defense.repository.DefenseGradeRepository;
import fr.epita.platform.apps.defense.repository.DefenseGroupRepository;
import fr.epita.platform.apps.defense.repository.DefenseSlotRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Contraintes : jury de 2 ou 3 enseignants, pas d'enseignant ni de salle sur deux créneaux qui se chevauchent,
 * encadrant hors du jury de son groupe. Un groupe choisit un créneau libre (sous verrou), modifiable jusqu'à 48 h avant.
 */
@Service
@RequiredArgsConstructor
public class DefenseService {

    public static final String ROLES = "defense_roles";
    public static final String TEACHER = "TEACHER";
    static final Duration CHANGE_LIMIT = Duration.ofHours(48);
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final DefenseSlotRepository slots;
    private final DefenseGroupRepository groups;
    private final DefenseGradeRepository grades;
    private final AppRoles roles;
    private final UserLookup userLookup;
    private final Clock clock;

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(TEACHER));
    }

    // ---------- créneaux ----------

    @Transactional(readOnly = true)
    public List<SlotDto> slots(LocalDate date, Boolean free) {
        List<DefenseSlot> list = date == null ? slots.findAllByOrderByStartAtAsc()
                : slots.findByStartAtBetweenOrderByStartAtAsc(date.atStartOfDay(ZONE).toInstant(), date.plusDays(1).atStartOfDay(ZONE).toInstant());
        return list.stream().map(this::toDto).filter(s -> free == null || s.free() == free).toList();
    }

    @Transactional
    public SlotDto create(AuthUser me, SlotRequest req) {
        me.requireAdmin();
        DefenseSlot s = new DefenseSlot();
        apply(s, req);
        return toDto(slots.saveAndFlush(s));
    }

    @Transactional
    public SlotDto update(AuthUser me, UUID id, SlotRequest req) {
        me.requireAdmin();
        DefenseSlot s = slots.lock(id).orElseThrow(() -> ApiException.notFound("Créneau"));
        apply(s, req);
        return toDto(slots.saveAndFlush(s));
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        me.requireAdmin();
        DefenseSlot s = slots.lock(id).orElseThrow(() -> ApiException.notFound("Créneau"));
        if (groups.findBySlotId(id).isPresent()) {
            throw ApiException.conflict("SLOT_TAKEN", "Un groupe a choisi ce créneau");
        }
        slots.delete(s);
    }

    private void apply(DefenseSlot s, SlotRequest req) {
        Set<UUID> jury = new LinkedHashSet<>(req.juryIds());
        if (jury.size() < 2 || jury.size() > 3 || jury.size() != req.juryIds().size()) {
            throw ValidationException.field("juryIds", "2 ou 3 enseignants distincts");
        }
        for (UUID t : jury) {
            userLookup.require(t);
            if (!roles.hasExplicit(ROLES, t, TEACHER)) {
                throw ValidationException.field("juryIds", userLookup.name(t) + " n'a pas le rôle TEACHER");
            }
        }
        s.setStartAt(req.start());
        s.setDurationMinutes(req.durationMinutes());
        s.setRoom(req.room().trim());
        s.setJuryIds(new HashSet<>(jury));
        for (DefenseSlot other : slots.findAllByOrderByStartAtAsc()) {
            if (other.getId().equals(s.getId()) || !other.overlaps(s)) {
                continue;
            }
            if (other.getRoom().equalsIgnoreCase(s.getRoom())) {
                throw ApiException.conflict("ROOM_CONFLICT", "La salle " + s.getRoom() + " est déjà occupée sur ce créneau");
            }
            Optional<UUID> busy = other.getJuryIds().stream().filter(jury::contains).findFirst();
            if (busy.isPresent()) {
                throw ApiException.conflict("TEACHER_CONFLICT", userLookup.name(busy.get()) + " siège déjà dans un jury sur ce créneau");
            }
        }
        if (s.getId() != null) {
            groups.findBySlotId(s.getId()).ifPresent(g -> requireSupervisorOutside(g, s));
        }
    }

    private void requireSupervisorOutside(DefenseGroup g, DefenseSlot s) {
        if (s.getJuryIds().contains(g.getSupervisorId())) {
            throw ApiException.conflict("SUPERVISOR_IN_JURY", "L'encadrant du groupe ne peut pas siéger dans son jury");
        }
    }

    // ---------- groupes ----------

    @Transactional(readOnly = true)
    public List<GroupDto> groups(AuthUser me) {
        if (me.isAdmin() || roles.hasExplicit(ROLES, me.id(), TEACHER)) {
            return groups.findAllByOrderByNameAsc().stream().map(g -> toDto(me, g)).toList();
        }
        return groups.ofStudent(me.id()).stream().map(g -> toDto(me, g)).toList();
    }

    @Transactional
    public GroupDto createGroup(AuthUser me, GroupRequest req) {
        me.requireAdmin();
        if (groups.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("GROUP_EXISTS", "Un groupe porte déjà ce nom");
        }
        if (!roles.hasExplicit(ROLES, req.supervisorId(), TEACHER)) {
            throw ValidationException.field("supervisorId", "l'encadrant doit avoir le rôle TEACHER");
        }
        for (int i = 0; i < req.studentIds().size(); i++) {
            UUID s = req.studentIds().get(i);
            userLookup.require(s);
            if (groups.ofStudent(s).isPresent()) {
                throw ValidationException.field("studentIds[" + i + "]", userLookup.name(s) + " est déjà dans un groupe");
            }
        }
        DefenseGroup g = new DefenseGroup();
        g.setName(req.name().trim());
        g.setSupervisorId(req.supervisorId());
        g.setStudentIds(new HashSet<>(req.studentIds()));
        return toDto(me, groups.saveAndFlush(g));
    }

    /** Choix (ou changement) de créneau : libre, encadrant hors jury, au plus tard 48 h avant. */
    @Transactional
    public GroupDto choose(AuthUser me, UUID groupId, UUID slotId) {
        DefenseGroup g = groups.findById(groupId).orElseThrow(() -> ApiException.notFound("Groupe"));
        if (!g.getStudentIds().contains(me.id())) {
            throw ApiException.forbidden("Réservé aux étudiants du groupe");
        }
        var limit = clock.instant().plus(CHANGE_LIMIT);
        if (g.getSlotId() != null) {
            DefenseSlot current = slots.findById(g.getSlotId()).orElseThrow();
            if (current.getStartAt().isBefore(limit)) {
                throw ApiException.conflict("TOO_LATE", "Changement impossible à moins de 48 h de la soutenance");
            }
        }
        DefenseSlot s = slots.lock(slotId).orElseThrow(() -> ApiException.notFound("Créneau"));
        if (s.getStartAt().isBefore(limit)) {
            throw ApiException.conflict("TOO_LATE", "Ce créneau commence dans moins de 48 h");
        }
        Optional<DefenseGroup> holder = groups.findBySlotId(slotId);
        if (holder.isPresent() && !holder.get().getId().equals(groupId)) {
            throw ApiException.conflict("SLOT_TAKEN", "Ce créneau vient d'être choisi par un autre groupe");
        }
        requireSupervisorOutside(g, s);
        g.setSlotId(slotId);
        return toDto(me, groups.saveAndFlush(g));
    }

    // ---------- jurys et notes ----------

    @Transactional(readOnly = true)
    public List<JuryDutyDto> myJury(AuthUser me) {
        roles.require(ROLES, me, TEACHER);
        return slots.ofTeacher(me.id()).stream().map(s -> {
            Optional<DefenseGrade> mine = grades.findById(new DefenseGrade.Key(s.getId(), me.id()));
            boolean done = !clock.instant().isBefore(s.endAt());
            boolean hasGroup = groups.findBySlotId(s.getId()).isPresent();
            return new JuryDutyDto(toDto(s), mine.map(DefenseGrade::getGrade).orElse(null), mine.map(DefenseGrade::getComment).orElse(null), done, done && hasGroup);
        }).toList();
    }

    @Transactional
    public JuryDutyDto grade(AuthUser me, UUID slotId, GradeRequest req) {
        DefenseSlot s = slots.lock(slotId).orElseThrow(() -> ApiException.notFound("Créneau"));
        if (!s.getJuryIds().contains(me.id())) {
            throw ApiException.forbidden("Réservé aux membres du jury");
        }
        if (groups.findBySlotId(slotId).isEmpty()) {
            throw ApiException.conflict("NO_GROUP", "Aucun groupe n'a soutenu sur ce créneau");
        }
        if (clock.instant().isBefore(s.endAt())) {
            throw ApiException.conflict("DEFENSE_NOT_DONE", "La note se saisit après la soutenance");
        }
        if (req.grade().remainder(new BigDecimal("0.25")).signum() != 0) {
            throw ValidationException.field("grade", "au quart de point (ex. 14.25)");
        }
        grades.saveAndFlush(new DefenseGrade(new DefenseGrade.Key(slotId, me.id()), req.grade(), req.comment().trim()));
        return new JuryDutyDto(toDto(s), req.grade(), req.comment().trim(), true, true);
    }

    /** Note finale publiée quand tout le jury a noté ; visible des étudiants du groupe, de l'encadrant, du jury et des ADMIN. */
    @Transactional(readOnly = true)
    public ResultDto result(AuthUser me, UUID groupId) {
        DefenseGroup g = groups.findById(groupId).orElseThrow(() -> ApiException.notFound("Groupe"));
        DefenseSlot s = g.getSlotId() == null ? null : slots.findById(g.getSlotId()).orElse(null);
        boolean allowed = me.isAdmin() || g.getStudentIds().contains(me.id()) || g.getSupervisorId().equals(me.id())
                || (s != null && s.getJuryIds().contains(me.id()));
        if (!allowed) {
            throw ApiException.notFound("Groupe");
        }
        if (s == null) {
            return new ResultDto(groupId, g.getName(), false, null, 0, 0, List.of());
        }
        List<DefenseGrade> gs = grades.findByIdSlotId(s.getId()).stream().filter(x -> s.getJuryIds().contains(x.getId().getTeacherId())).toList();
        boolean published = gs.size() == s.getJuryIds().size();
        BigDecimal avg = published ? gs.stream().map(DefenseGrade::getGrade).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(gs.size()), 2, RoundingMode.HALF_UP) : null;
        List<GradeDto> details = published ? gs.stream().map(x -> new GradeDto(userLookup.name(x.getId().getTeacherId()), x.getGrade(), x.getComment())).toList() : List.of();
        return new ResultDto(groupId, g.getName(), published, avg, gs.size(), s.getJuryIds().size(), details);
    }

    @Transactional(readOnly = true)
    public List<PlanningRow> planning(String by) {
        List<DefenseSlot> all = slots.findAllByOrderByStartAtAsc();
        if ("teacher".equals(by)) {
            Map<UUID, List<DefenseSlot>> byTeacher = new HashMap<>();
            all.forEach(s -> s.getJuryIds().forEach(t -> byTeacher.computeIfAbsent(t, k -> new ArrayList<>()).add(s)));
            Map<UUID, String> names = userLookup.names(byTeacher.keySet());
            return byTeacher.entrySet().stream().map(e -> new PlanningRow(names.get(e.getKey()), e.getValue().stream().map(this::toDto).toList()))
                    .sorted(Comparator.comparing(PlanningRow::label, Comparator.nullsLast(String::compareTo))).toList();
        }
        if (by != null && !"room".equals(by)) {
            throw ValidationException.field("by", "doit valoir room ou teacher");
        }
        return all.stream().collect(Collectors.groupingBy(DefenseSlot::getRoom, TreeMap::new, Collectors.toList())).entrySet().stream()
                .map(e -> new PlanningRow(e.getKey(), e.getValue().stream().map(this::toDto).toList())).toList();
    }

    // ---------- conversions ----------

    private SlotDto toDto(DefenseSlot s) {
        Set<UUID> graded = grades.findByIdSlotId(s.getId()).stream().map(g -> g.getId().getTeacherId()).collect(Collectors.toSet());
        Map<UUID, String> names = userLookup.names(s.getJuryIds());
        List<JurorDto> jury = s.getJuryIds().stream().map(t -> new JurorDto(t, names.get(t), graded.contains(t)))
                .sorted(Comparator.comparing(JurorDto::name, Comparator.nullsLast(String::compareTo))).toList();
        Optional<DefenseGroup> g = groups.findBySlotId(s.getId());
        return new SlotDto(s.getId(), s.getStartAt(), s.endAt(), s.getDurationMinutes(), s.getRoom(), jury,
                g.map(DefenseGroup::getId).orElse(null), g.map(DefenseGroup::getName).orElse(null), g.isEmpty());
    }

    private GroupDto toDto(AuthUser me, DefenseGroup g) {
        Map<UUID, String> names = userLookup.names(g.getStudentIds());
        List<StudentDto> students = g.getStudentIds().stream().map(u -> new StudentDto(u, names.get(u)))
                .sorted(Comparator.comparing(StudentDto::name, Comparator.nullsLast(String::compareTo))).toList();
        SlotDto slot = g.getSlotId() == null ? null : slots.findById(g.getSlotId()).map(this::toDto).orElse(null);
        return new GroupDto(g.getId(), g.getName(), students, g.getSupervisorId(), userLookup.name(g.getSupervisorId()), slot, g.getStudentIds().contains(me.id()));
    }
}
