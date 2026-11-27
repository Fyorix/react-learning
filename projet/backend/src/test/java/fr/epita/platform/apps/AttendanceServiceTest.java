package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.attendance.dto.AttendanceDtos.*;
import fr.epita.platform.apps.attendance.entity.JustificationStatus;
import fr.epita.platform.apps.attendance.entity.PresenceStatus;
import fr.epita.platform.apps.attendance.service.AttendanceService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttendanceServiceTest extends IntegrationTest {

    @Autowired
    AttendanceService service;
    @Autowired
    AppRoles roles;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser admin;
    AuthUser prof;
    AuthUser other;
    AuthUser s1;
    AuthUser s2;
    GroupDto td;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        prof = teacher("Prof");
        other = teacher("Autre prof");
        s1 = auth(user("Élève Un"));
        s2 = auth(user("Élève Deux"));
        td = service.createGroup(admin, new GroupRequest("TD1", List.of(s1.id(), s2.id())));
    }

    private AuthUser teacher(String name) {
        AuthUser t = auth(user(name));
        roles.grant(AttendanceService.ROLES, Set.of(AttendanceService.TEACHER), t.id(), AttendanceService.TEACHER);
        return t;
    }

    /** Séance commencée il y a `hoursAgo` heures, d'une heure. */
    private SessionDto session(String subject, int hoursAgo) {
        Instant start = Instant.now().minus(hoursAgo, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MINUTES);
        return service.createSessions(admin, new SessionRequest(subject, td.id(), prof.id(), start, start.plus(1, ChronoUnit.HOURS), 1)).get(0);
    }

    private void call(SessionDto s, PresenceStatus a, PresenceStatus b) {
        service.rollCall(prof, s.id(), List.of(new RollCallEntry(s1.id(), a), new RollCallEntry(s2.id(), b)));
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "certificat.pdf", "application/pdf", "%PDF-1.4 test".getBytes());
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void rollCallWindowAndOwnership() {
        SessionDto s = session("React", 1);
        assertCode(() -> service.rollCall(other, s.id(), List.of()), "FORBIDDEN");
        call(s, PresenceStatus.PRESENT, PresenceStatus.ABSENT);
        assertThat(service.session(prof, s.id()).lines()).extracting(RollCallLine::status).containsExactlyInAnyOrder(PresenceStatus.PRESENT, PresenceStatus.ABSENT);
        jdbc.update("update attendance_sessions set end_at = now() - interval '25 hours', start_at = now() - interval '26 hours' where id = ?", s.id());
        assertCode(() -> call(s, PresenceStatus.PRESENT, PresenceStatus.PRESENT), "ATTENDANCE_LOCKED");
        Instant future = Instant.now().plus(2, ChronoUnit.DAYS);
        SessionDto later = service.createSessions(admin, new SessionRequest("React", td.id(), prof.id(), future, future.plus(1, ChronoUnit.HOURS), 3)).get(0);
        assertCode(() -> call(later, PresenceStatus.PRESENT, PresenceStatus.PRESENT), "SESSION_NOT_STARTED");
        assertThat(service.teacherSessions(prof, null)).hasSize(4);
    }

    @Test
    void justificationFlow() {
        SessionDto s = session("React", 2);
        call(s, PresenceStatus.ABSENT, PresenceStatus.PRESENT);
        AbsenceDto abs = service.mine(s1).absences().get(0);
        assertThat(abs.justifiable()).isTrue();
        assertCode(() -> service.justify(s2, new JustificationRequest(List.of(abs.id()), "x"), pdf()), "NOT_FOUND");
        JustificationDto j = service.justify(s1, new JustificationRequest(List.of(abs.id()), "Rendez-vous médical"), pdf());
        assertThat(j.status()).isEqualTo(JustificationStatus.PENDING);
        assertCode(() -> service.justify(s1, new JustificationRequest(List.of(abs.id()), "bis"), pdf()), "ALREADY_JUSTIFIED");
        assertThat(service.file(s1, j.id()).content()).isNotEmpty();
        assertCode(() -> service.file(s2, j.id()), "NOT_FOUND");
        service.decide(admin, j.id(), false, "Illisible");
        JustificationDto again = service.justify(s1, new JustificationRequest(List.of(abs.id()), "Nouveau scan"), pdf());
        service.decide(admin, again.id(), true, null);
        CounterDto c = service.mine(s1).counters().get(0);
        assertThat(c.justified()).isEqualTo(1);
        assertThat(c.unjustified()).isZero();
    }

    @Test
    void justificationsAreRefusedAfterSevenDays() {
        SessionDto s = session("React", 1);
        call(s, PresenceStatus.ABSENT, PresenceStatus.PRESENT);
        UUID id = service.mine(s1).absences().get(0).id();
        jdbc.update("update attendance_sessions set start_at = now() - interval '9 days', end_at = now() - interval '8 days' where id = ?", s.id());
        assertCode(() -> service.justify(s1, new JustificationRequest(List.of(id), "Trop tard"), pdf()), "JUSTIFICATION_TOO_LATE");
    }

    @Test
    void threeLatesCountAsOneAbsenceAndAlertAboveThree() {
        for (int i = 0; i < 3; i++) {
            call(session("Java", 1 + i), PresenceStatus.ABSENT, PresenceStatus.LATE);
        }
        for (int i = 0; i < 3; i++) {
            call(session("Java", 5 + i), PresenceStatus.LATE, PresenceStatus.LATE);
        }
        CounterDto c1 = service.mine(s1).counters().get(0);
        assertThat(c1.unjustified()).isEqualTo(3);
        assertThat(c1.late()).isEqualTo(3);
        assertThat(c1.effectiveUnjustified()).isEqualTo(4);
        assertThat(c1.alert()).isTrue();
        CounterDto c2 = service.mine(s2).counters().get(0);
        assertThat(c2.effectiveUnjustified()).isEqualTo(2);
        assertThat(service.alerts(admin)).singleElement().satisfies(a -> assertThat(a.studentId()).isEqualTo(s1.id()));
    }
}
