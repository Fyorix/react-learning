package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.grade.dto.GradeDtos.*;
import fr.epita.platform.apps.grade.entity.MarkStatus;
import fr.epita.platform.apps.grade.service.GradeService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeServiceTest extends IntegrationTest {

    @Autowired
    GradeService service;
    @Autowired
    AppRoles roles;

    AuthUser admin;
    AuthUser prof;
    AuthUser other;
    AuthUser s1;
    AuthUser s2;
    ClassDto mti;
    SubjectDto react;
    SubjectDto java;
    PeriodDto sem;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        prof = auth(user("Prof React"));
        other = auth(user("Prof Java"));
        roles.grant(GradeService.ROLES, Set.of(GradeService.TEACHER), prof.id(), GradeService.TEACHER);
        roles.grant(GradeService.ROLES, Set.of(GradeService.TEACHER), other.id(), GradeService.TEACHER);
        s1 = auth(user("Élève Un"));
        s2 = auth(user("Élève Deux"));
        mti = service.createClass(admin, new ClassRequest("MTI A", List.of(s1.id(), s2.id())));
        react = service.createSubject(admin, new SubjectRequest("React", new BigDecimal("3"), prof.id(), mti.id()));
        java = service.createSubject(admin, new SubjectRequest("Java", new BigDecimal("1"), other.id(), mti.id()));
        sem = service.createPeriod(admin, new PeriodRequest("S1", LocalDate.of(2026, 9, 1), LocalDate.of(2027, 1, 31)));
    }

    private AssessmentDto exam(AuthUser who, SubjectDto s, String title, String weight) {
        return service.createAssessment(who, s.id(), new AssessmentRequest(sem.id(), title, LocalDate.of(2026, 11, 10), new BigDecimal(weight)));
    }

    private MarkInput mark(AuthUser s, String v) {
        return new MarkInput(s.id(), v == null ? null : new BigDecimal(v), null);
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void weightedAveragesAndAbsences() {
        AssessmentDto a = exam(prof, react, "TP", "1");
        AssessmentDto b = exam(prof, react, "Partiel", "2");
        AssessmentDto c = exam(other, java, "QCM", "1");
        service.saveMarks(prof, a.id(), List.of(mark(s1, "10"), new MarkInput(s2.id(), null, MarkStatus.ABSENT)));
        service.saveMarks(prof, b.id(), List.of(mark(s1, "16"), mark(s2, "12")));
        service.saveMarks(other, c.id(), List.of(new MarkInput(s1.id(), null, MarkStatus.ABSENT_EXCUSED), mark(s2, "18")));
        MyGradesDto g1 = service.mine(s1, sem.id());
        assertThat(g1.subjects().stream().filter(x -> x.subjectName().equals("React")).findFirst().orElseThrow().average()).isEqualByComparingTo("14.00");
        assertThat(g1.subjects().stream().filter(x -> x.subjectName().equals("Java")).findFirst().orElseThrow().average()).isNull();
        assertThat(g1.general()).isEqualByComparingTo("14.00");
        // s2 : React (0×1 + 12×2) / 3 = 8 ; Java 18 ; générale (8×3 + 18×1) / 4 = 10.50
        assertThat(service.mine(s2, sem.id()).general()).isEqualByComparingTo("10.50");
        ReportCardDto card = service.reportCard(prof, s2.id(), sem.id());
        assertThat(card.rank()).isEqualTo(2);
        assertThat(service.classSummary(admin, mti.id(), sem.id()).classAverage()).isEqualByComparingTo("12.25");
    }

    @Test
    void teachersOnlyGradeTheirSubjectsWithQuarterPoints() {
        AssessmentDto a = exam(prof, react, "TP", "1");
        assertCode(() -> service.saveMarks(other, a.id(), List.of(mark(s1, "10"))), "FORBIDDEN");
        assertThatThrownBy(() -> service.saveMarks(prof, a.id(), List.of(mark(s1, "10.1")))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.saveMarks(prof, a.id(), List.of(new MarkInput(admin.id(), new BigDecimal("10"), null)))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.createAssessment(prof, react.id(), new AssessmentRequest(sem.id(), "Hors période", LocalDate.of(2027, 3, 1), BigDecimal.ONE)))
                .isInstanceOf(ValidationException.class);
        assertThat(service.saveMarks(prof, a.id(), List.of(mark(s1, "14.75"))).marks()).extracting(MarkDto::value).contains(new BigDecimal("14.75"));
    }

    @Test
    void lockedPeriodRefusesInputAndOpensReportCards() {
        AssessmentDto a = exam(prof, react, "TP", "1");
        service.saveMarks(prof, a.id(), List.of(mark(s1, "12")));
        assertCode(() -> service.reportCard(s1, s1.id(), sem.id()), "PERIOD_NOT_LOCKED");
        assertCode(() -> service.reportCard(s2, s1.id(), sem.id()), "NOT_FOUND");
        service.saveComments(prof, react.id(), sem.id(), List.of(new CommentInput(s1.id(), "Bon travail")));
        service.saveGeneralComment(admin, s1.id(), sem.id(), "Semestre réussi");
        service.setLocked(admin, sem.id(), true);
        assertCode(() -> service.saveMarks(prof, a.id(), List.of(mark(s1, "13"))), "PERIOD_LOCKED");
        assertCode(() -> exam(prof, react, "Trop tard", "1"), "PERIOD_LOCKED");
        ReportCardDto card = service.reportCard(s1, s1.id(), sem.id());
        assertThat(card.generalComment()).isEqualTo("Semestre réussi");
        assertThat(card.subjects().get(1).comment()).isEqualTo("Bon travail");
        assertThat(card.rank()).isEqualTo(1);
        service.setLocked(admin, sem.id(), false);
        assertThat(service.saveMarks(prof, a.id(), List.of(mark(s1, "13"))).marks()).isNotEmpty();
    }

    @Test
    void adminSetupRules() {
        assertCode(() -> service.createClass(admin, new ClassRequest("mti a", List.of())), "CLASS_EXISTS");
        assertThatThrownBy(() -> service.createClass(admin, new ClassRequest("MTI B", List.of(s1.id())))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.createSubject(admin, new SubjectRequest("Maths", BigDecimal.ONE, s1.id(), mti.id()))).isInstanceOf(ValidationException.class);
        assertCode(() -> service.createSubject(admin, new SubjectRequest("react", BigDecimal.ONE, prof.id(), mti.id())), "SUBJECT_EXISTS");
        assertCode(() -> service.createPeriod(prof, new PeriodRequest("S2", LocalDate.of(2027, 2, 1), LocalDate.of(2027, 6, 30))), "FORBIDDEN");
        assertThat(service.teacherSubjects(prof)).extracting(SubjectDto::name).containsExactly("React");
    }
}
