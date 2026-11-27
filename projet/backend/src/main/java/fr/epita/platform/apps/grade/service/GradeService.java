package fr.epita.platform.apps.grade.service;

import fr.epita.platform.apps.grade.dto.GradeDtos.*;
import fr.epita.platform.apps.grade.entity.*;
import fr.epita.platform.apps.grade.repository.*;
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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Moyenne d'une matière : notes pondérées par le poids des évaluations (ABSENT compte 0, ABSENT_EXCUSED exclue) ;
 * moyenne générale : matières pondérées par leur coefficient, matières sans note exclues ; arrondi à 2 décimales.
 * Une période verrouillée n'accepte plus aucune saisie ; le bulletin n'est visible de l'étudiant qu'après verrouillage.
 */
@Service
@RequiredArgsConstructor
public class GradeService {

    public static final String ROLES = "grade_roles";
    public static final String TEACHER = "TEACHER";
    private static final BigDecimal QUARTER = new BigDecimal("0.25");

    private final SchoolClassRepository classes;
    private final SubjectRepository subjects;
    private final GradePeriodRepository periods;
    private final AssessmentRepository assessments;
    private final MarkRepository marks;
    private final GradeCommentRepository comments;
    private final AppRoles roles;
    private final UserLookup userLookup;

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(TEACHER));
    }

    // ---------- administration ----------

    @Transactional(readOnly = true)
    public List<ClassDto> classes(AuthUser me) {
        requireStaff(me);
        return classes.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public ClassDto createClass(AuthUser me, ClassRequest req) {
        me.requireAdmin();
        if (classes.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("CLASS_EXISTS", "Une classe porte déjà ce nom");
        }
        for (int i = 0; i < req.studentIds().size(); i++) {
            UUID s = req.studentIds().get(i);
            userLookup.require(s);
            if (classes.ofStudent(s).isPresent()) {
                throw ValidationException.field("studentIds[" + i + "]", userLookup.name(s) + " est déjà dans une classe");
            }
        }
        SchoolClass c = new SchoolClass();
        c.setName(req.name().trim());
        c.setStudentIds(new HashSet<>(req.studentIds()));
        return toDto(classes.saveAndFlush(c));
    }

    @Transactional(readOnly = true)
    public List<SubjectDto> allSubjects(AuthUser me) {
        me.requireAdmin();
        return subjects.findAll().stream().map(this::toDto).sorted(Comparator.comparing(SubjectDto::className).thenComparing(SubjectDto::name)).toList();
    }

    @Transactional
    public SubjectDto createSubject(AuthUser me, SubjectRequest req) {
        me.requireAdmin();
        schoolClass(req.classId());
        if (!roles.hasExplicit(ROLES, req.teacherId(), TEACHER)) {
            throw ValidationException.field("teacherId", "l'enseignant doit avoir le rôle TEACHER");
        }
        if (subjects.existsByClassIdAndNameIgnoreCase(req.classId(), req.name().trim())) {
            throw ApiException.conflict("SUBJECT_EXISTS", "Cette matière existe déjà dans la classe");
        }
        Subject s = new Subject();
        s.setName(req.name().trim());
        s.setCoefficient(req.coefficient());
        s.setTeacherId(req.teacherId());
        s.setClassId(req.classId());
        return toDto(subjects.saveAndFlush(s));
    }

    @Transactional(readOnly = true)
    public List<PeriodDto> periods() {
        return periods.findAllByOrderByStartDateAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public PeriodDto createPeriod(AuthUser me, PeriodRequest req) {
        me.requireAdmin();
        if (req.end().isBefore(req.start())) {
            throw ValidationException.field("end", "doit être postérieure ou égale au début");
        }
        if (periods.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("PERIOD_EXISTS", "Une période porte déjà ce nom");
        }
        GradePeriod p = new GradePeriod();
        p.setName(req.name().trim());
        p.setStartDate(req.start());
        p.setEndDate(req.end());
        p.setLocked(false);
        return toDto(periods.saveAndFlush(p));
    }

    @Transactional
    public PeriodDto setLocked(AuthUser me, UUID id, boolean locked) {
        me.requireAdmin();
        GradePeriod p = periods.lock(id).orElseThrow(() -> ApiException.notFound("Période"));
        p.setLocked(locked);
        return toDto(periods.saveAndFlush(p));
    }

    // ---------- saisie enseignant ----------

    @Transactional(readOnly = true)
    public List<SubjectDto> teacherSubjects(AuthUser me) {
        roles.require(ROLES, me, TEACHER);
        List<Subject> list = me.isAdmin() && !roles.hasExplicit(ROLES, me.id(), TEACHER) ? subjects.findAll() : subjects.findByTeacherIdOrderByNameAsc(me.id());
        return list.stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AssessmentDto> subjectAssessments(AuthUser me, UUID subjectId) {
        Subject s = ownSubject(me, subjectId);
        return assessments.findBySubjectIdOrderByDateAsc(s.getId()).stream().map(a -> toDto(a, s)).toList();
    }

    @Transactional
    public AssessmentDto createAssessment(AuthUser me, UUID subjectId, AssessmentRequest req) {
        Subject s = ownSubject(me, subjectId);
        GradePeriod p = openPeriod(req.periodId());
        if (req.date().isBefore(p.getStartDate()) || req.date().isAfter(p.getEndDate())) {
            throw ValidationException.field("date", "doit être comprise dans la période " + p.getName());
        }
        Assessment a = new Assessment();
        a.setSubjectId(s.getId());
        a.setPeriodId(p.getId());
        a.setTitle(req.title().trim());
        a.setDate(req.date());
        a.setWeight(req.weight());
        return toDto(assessments.saveAndFlush(a), s);
    }

    @Transactional(readOnly = true)
    public AssessmentDto assessment(AuthUser me, UUID id) {
        Assessment a = assessments.findById(id).orElseThrow(() -> ApiException.notFound("Évaluation"));
        return toDto(a, ownSubject(me, a.getSubjectId()));
    }

    @Transactional
    public AssessmentDto saveMarks(AuthUser me, UUID id, List<MarkInput> input) {
        Assessment a = assessments.findById(id).orElseThrow(() -> ApiException.notFound("Évaluation"));
        Subject s = ownSubject(me, a.getSubjectId());
        openPeriod(a.getPeriodId());
        Set<UUID> students = schoolClass(s.getClassId()).getStudentIds();
        var errors = new ValidationException.Collector();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < input.size(); i++) {
            MarkInput m = input.get(i);
            String f = "marks[" + i + "]";
            errors.check(students.contains(m.studentId()), f + ".studentId", "étudiant hors de la classe");
            errors.check(seen.add(m.studentId()), f + ".studentId", "étudiant en double");
            MarkStatus st = status(m);
            errors.check(st != MarkStatus.GRADED || m.value() != null, f + ".value", "note obligatoire (ou statut d'absence)");
            errors.check(st == MarkStatus.GRADED || m.value() == null, f + ".value", "pas de note pour une absence");
            errors.check(m.value() == null || m.value().remainder(QUARTER).signum() == 0, f + ".value", "au quart de point (ex. 14.25)");
        }
        errors.throwIfAny();
        input.forEach(m -> marks.save(new Mark(new Mark.Key(a.getId(), m.studentId()), status(m) == MarkStatus.GRADED ? m.value() : null, status(m))));
        marks.flush();
        return toDto(a, s);
    }

    @Transactional
    public List<CommentInput> saveComments(AuthUser me, UUID subjectId, UUID periodId, List<CommentInput> input) {
        Subject s = ownSubject(me, subjectId);
        openPeriod(periodId);
        Set<UUID> students = schoolClass(s.getClassId()).getStudentIds();
        for (int i = 0; i < input.size(); i++) {
            if (!students.contains(input.get(i).studentId())) {
                throw ValidationException.field("comments[" + i + "].studentId", "étudiant hors de la classe");
            }
        }
        Map<UUID, GradeComment> existing = comments.findByPeriodIdAndSubjectId(periodId, subjectId).stream()
                .collect(Collectors.toMap(GradeComment::getStudentId, c -> c));
        for (CommentInput c : input) {
            upsertComment(existing.get(c.studentId()), periodId, c.studentId(), subjectId, c.text());
        }
        comments.flush();
        return comments.findByPeriodIdAndSubjectId(periodId, subjectId).stream().map(c -> new CommentInput(c.getStudentId(), c.getText())).toList();
    }

    @Transactional
    public ReportCardDto saveGeneralComment(AuthUser me, UUID studentId, UUID periodId, String text) {
        me.requireAdmin();
        openPeriod(periodId);
        classOfStudent(studentId);
        GradeComment existing = comments.findByPeriodIdAndStudentId(periodId, studentId).stream().filter(c -> c.getSubjectId() == null).findFirst().orElse(null);
        upsertComment(existing, periodId, studentId, null, text);
        comments.flush();
        return reportCard(me, studentId, periodId);
    }

    private void upsertComment(GradeComment existing, UUID periodId, UUID studentId, UUID subjectId, String text) {
        if (text.isBlank()) {
            if (existing != null) {
                comments.delete(existing);
            }
            return;
        }
        GradeComment c = existing == null ? new GradeComment() : existing;
        if (existing == null) {
            c.setId(UUID.randomUUID());
            c.setPeriodId(periodId);
            c.setStudentId(studentId);
            c.setSubjectId(subjectId);
        }
        c.setText(text.trim());
        comments.save(c);
    }

    // ---------- consultation ----------

    @Transactional(readOnly = true)
    public MyGradesDto mine(AuthUser me, UUID periodId) {
        SchoolClass c = classOfStudent(me.id());
        GradePeriod p = period(periodId);
        Computed r = compute(c, p, me.id());
        return new MyGradesDto(p.getId(), p.getName(), p.isLocked(), c.getName(), r.subjects(), r.general());
    }

    @Transactional(readOnly = true)
    public ReportCardDto reportCard(AuthUser me, UUID studentId, UUID periodId) {
        SchoolClass c = classOfStudent(studentId);
        GradePeriod p = period(periodId);
        boolean staff = me.isAdmin() || subjects.existsByClassIdAndTeacherId(c.getId(), me.id());
        if (!staff) {
            if (!me.id().equals(studentId)) {
                throw ApiException.notFound("Bulletin");
            }
            if (!p.isLocked()) {
                throw ApiException.conflict("PERIOD_NOT_LOCKED", "Le bulletin sera disponible après le verrouillage de la période");
            }
        }
        Computed r = compute(c, p, studentId);
        Map<UUID, BigDecimal> generals = c.getStudentIds().stream().collect(HashMap::new, (m, s) -> m.put(s, compute(c, p, s).general()), HashMap::putAll);
        Integer rank = rank(generals, studentId);
        String general = comments.findByPeriodIdAndStudentId(periodId, studentId).stream().filter(x -> x.getSubjectId() == null).map(GradeComment::getText).findFirst().orElse(null);
        return new ReportCardDto(studentId, userLookup.name(studentId), c.getName(), p.getId(), p.getName(), p.isLocked(), r.subjects(), r.general(), rank, c.getStudentIds().size(), general);
    }

    @Transactional(readOnly = true)
    public ClassSummaryDto classSummary(AuthUser me, UUID classId, UUID periodId) {
        SchoolClass c = schoolClass(classId);
        if (!me.isAdmin() && !subjects.existsByClassIdAndTeacherId(classId, me.id())) {
            throw ApiException.forbidden("Réservé aux enseignants de la classe et aux ADMIN");
        }
        GradePeriod p = period(periodId);
        List<Subject> subs = subjects.findByClassIdOrderByNameAsc(classId);
        Map<UUID, Computed> byStudent = new HashMap<>();
        c.getStudentIds().forEach(s -> byStudent.put(s, compute(c, p, s)));
        Map<UUID, BigDecimal> generals = new HashMap<>();
        byStudent.forEach((s, r) -> generals.put(s, r.general()));
        Map<UUID, String> names = userLookup.names(c.getStudentIds());
        List<SummaryRow> rows = c.getStudentIds().stream().map(s -> new SummaryRow(s, names.get(s),
                        byStudent.get(s).subjects().stream().map(SubjectAverage::average).toList(), generals.get(s), rank(generals, s)))
                .sorted(Comparator.comparing(SummaryRow::studentName, Comparator.nullsLast(String::compareTo))).toList();
        List<BigDecimal> subjectAvgs = new ArrayList<>();
        for (int i = 0; i < subs.size(); i++) {
            int col = i;
            subjectAvgs.add(mean(rows.stream().map(r -> r.subjectAverages().get(col)).filter(Objects::nonNull).toList()));
        }
        return new ClassSummaryDto(c.getId(), c.getName(), p.getId(), p.getName(), subs.stream().map(this::toDto).toList(), rows, subjectAvgs,
                mean(rows.stream().map(SummaryRow::general).filter(Objects::nonNull).toList()));
    }

    // ---------- calcul ----------

    record Computed(List<SubjectAverage> subjects, BigDecimal general) {
    }

    private Computed compute(SchoolClass c, GradePeriod p, UUID studentId) {
        List<Subject> subs = subjects.findByClassIdOrderByNameAsc(c.getId());
        List<Assessment> all = assessments.findBySubjectIdInAndPeriodIdOrderByDateAsc(subs.stream().map(Subject::getId).toList(), p.getId());
        Map<UUID, Mark> mine = marks.findByIdAssessmentIdIn(all.stream().map(Assessment::getId).toList()).stream()
                .filter(m -> m.getId().getStudentId().equals(studentId)).collect(Collectors.toMap(m -> m.getId().getAssessmentId(), m -> m));
        Map<UUID, String> subjectComments = comments.findByPeriodIdAndStudentId(p.getId(), studentId).stream().filter(x -> x.getSubjectId() != null)
                .collect(Collectors.toMap(GradeComment::getSubjectId, GradeComment::getText));
        List<SubjectAverage> result = new ArrayList<>();
        BigDecimal weighted = BigDecimal.ZERO;
        BigDecimal coefs = BigDecimal.ZERO;
        for (Subject s : subs) {
            BigDecimal sum = BigDecimal.ZERO;
            BigDecimal weights = BigDecimal.ZERO;
            List<MyMarkDto> list = new ArrayList<>();
            for (Assessment a : all.stream().filter(x -> x.getSubjectId().equals(s.getId())).toList()) {
                Mark m = mine.get(a.getId());
                list.add(new MyMarkDto(a.getId(), a.getTitle(), a.getDate(), a.getWeight(), m == null ? null : m.getValue(), m == null ? null : m.getStatus()));
                if (m == null || m.getStatus() == MarkStatus.ABSENT_EXCUSED) {
                    continue;
                }
                BigDecimal v = m.getStatus() == MarkStatus.ABSENT ? BigDecimal.ZERO : m.getValue();
                sum = sum.add(v.multiply(a.getWeight()));
                weights = weights.add(a.getWeight());
            }
            BigDecimal avg = weights.signum() == 0 ? null : sum.divide(weights, 2, RoundingMode.HALF_UP);
            if (avg != null) {
                weighted = weighted.add(avg.multiply(s.getCoefficient()));
                coefs = coefs.add(s.getCoefficient());
            }
            result.add(new SubjectAverage(s.getId(), s.getName(), s.getCoefficient(), userLookup.name(s.getTeacherId()), avg, list, subjectComments.get(s.getId())));
        }
        return new Computed(result, coefs.signum() == 0 ? null : weighted.divide(coefs, 2, RoundingMode.HALF_UP));
    }

    /** Rang : 1 + nombre d'étudiants strictement devant (ex æquo au même rang) ; nul sans moyenne. */
    static Integer rank(Map<UUID, BigDecimal> generals, UUID studentId) {
        BigDecimal mine = generals.get(studentId);
        if (mine == null) {
            return null;
        }
        return 1 + (int) generals.values().stream().filter(g -> g != null && g.compareTo(mine) > 0).count();
    }

    private static BigDecimal mean(List<BigDecimal> values) {
        return values.isEmpty() ? null : values.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private static MarkStatus status(MarkInput m) {
        return m.status() == null ? MarkStatus.GRADED : m.status();
    }

    // ---------- accès ----------

    private void requireStaff(AuthUser me) {
        if (!me.isAdmin() && !roles.hasExplicit(ROLES, me.id(), TEACHER)) {
            throw ApiException.forbidden("Réservé aux enseignants et aux ADMIN");
        }
    }

    private Subject ownSubject(AuthUser me, UUID id) {
        Subject s = subjects.findById(id).orElseThrow(() -> ApiException.notFound("Matière"));
        if (!me.isAdmin() && !s.getTeacherId().equals(me.id())) {
            throw ApiException.forbidden("Vous n'enseignez pas cette matière");
        }
        return s;
    }

    private GradePeriod openPeriod(UUID id) {
        GradePeriod p = periods.share(id).orElseThrow(() -> ApiException.notFound("Période"));
        if (p.isLocked()) {
            throw ApiException.conflict("PERIOD_LOCKED", "La période " + p.getName() + " est verrouillée");
        }
        return p;
    }

    private GradePeriod period(UUID id) {
        return periods.findById(id).orElseThrow(() -> ApiException.notFound("Période"));
    }

    private SchoolClass schoolClass(UUID id) {
        return classes.findById(id).orElseThrow(() -> ApiException.notFound("Classe"));
    }

    private SchoolClass classOfStudent(UUID studentId) {
        return classes.ofStudent(studentId).orElseThrow(() -> ApiException.notFound("Classe de l'étudiant"));
    }

    // ---------- conversions ----------

    private ClassDto toDto(SchoolClass c) {
        Map<UUID, String> names = userLookup.names(c.getStudentIds());
        return new ClassDto(c.getId(), c.getName(), c.getStudentIds().stream().map(s -> new StudentDto(s, names.get(s)))
                .sorted(Comparator.comparing(StudentDto::name, Comparator.nullsLast(String::compareTo))).toList());
    }

    private SubjectDto toDto(Subject s) {
        SchoolClass c = schoolClass(s.getClassId());
        return new SubjectDto(s.getId(), s.getName(), s.getCoefficient(), s.getTeacherId(), userLookup.name(s.getTeacherId()), c.getId(), c.getName(), c.getStudentIds().size());
    }

    private PeriodDto toDto(GradePeriod p) {
        return new PeriodDto(p.getId(), p.getName(), p.getStartDate(), p.getEndDate(), p.isLocked());
    }

    private AssessmentDto toDto(Assessment a, Subject s) {
        GradePeriod p = period(a.getPeriodId());
        Set<UUID> students = schoolClass(s.getClassId()).getStudentIds();
        Map<UUID, Mark> byStudent = marks.findByIdAssessmentIdIn(List.of(a.getId())).stream().collect(Collectors.toMap(m -> m.getId().getStudentId(), m -> m));
        Map<UUID, String> names = userLookup.names(students);
        List<MarkDto> list = students.stream().map(u -> {
            Mark m = byStudent.get(u);
            return new MarkDto(u, names.get(u), m == null ? null : m.getValue(), m == null ? null : m.getStatus());
        }).sorted(Comparator.comparing(MarkDto::studentName, Comparator.nullsLast(String::compareTo))).toList();
        BigDecimal avg = mean(list.stream().filter(m -> m.status() == MarkStatus.GRADED || m.status() == MarkStatus.ABSENT)
                .map(m -> m.value() == null ? BigDecimal.ZERO : m.value()).toList());
        return new AssessmentDto(a.getId(), s.getId(), s.getName(), p.getId(), p.getName(), p.isLocked(), a.getTitle(), a.getDate(), a.getWeight(), list, avg);
    }
}
