package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.defense.dto.DefenseDtos.*;
import fr.epita.platform.apps.defense.service.DefenseService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefenseServiceTest extends IntegrationTest {

    @Autowired
    DefenseService service;
    @Autowired
    AppRoles roles;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser admin;
    AuthUser t1;
    AuthUser t2;
    AuthUser t3;
    AuthUser s1;
    AuthUser s2;
    Instant day;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        t1 = teacher("Prof Un");
        t2 = teacher("Prof Deux");
        t3 = teacher("Prof Trois");
        s1 = auth(user("Étudiant Un"));
        s2 = auth(user("Étudiant Deux"));
        day = Instant.now().plus(5, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
    }

    private AuthUser teacher(String name) {
        AuthUser t = auth(user(name));
        roles.grant(DefenseService.ROLES, java.util.Set.of(DefenseService.TEACHER), t.id(), DefenseService.TEACHER);
        return t;
    }

    private SlotDto slot(int hour, String room, AuthUser... jury) {
        return service.create(admin, new SlotRequest(day.plus(hour, ChronoUnit.HOURS), 40, room, java.util.Arrays.stream(jury).map(AuthUser::id).toList()));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void juryAndRoomAndTeacherConflicts() {
        assertThatThrownBy(() -> slot(0, "A1", t1)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(admin, new SlotRequest(day, 40, "A1", List.of(t1.id(), s1.id())))).isInstanceOf(ValidationException.class);
        slot(0, "A1", t1, t2);
        assertCode(() -> slot(0, "A1", t3, t2), "ROOM_CONFLICT");
        assertCode(() -> slot(0, "B2", t1, t3), "TEACHER_CONFLICT");
        assertThat(slot(1, "B2", t1, t3).free()).isTrue();
    }

    @Test
    void groupsChooseFreeSlotsAndSupervisorStaysOutOfTheJury() {
        GroupDto g = service.createGroup(admin, new GroupRequest("Groupe 1", List.of(s1.id()), t1.id()));
        SlotDto withSupervisor = slot(0, "A1", t1, t2);
        SlotDto ok = slot(2, "A1", t2, t3);
        assertCode(() -> service.choose(s1, g.id(), withSupervisor.id()), "SUPERVISOR_IN_JURY");
        assertCode(() -> service.choose(s2, g.id(), ok.id()), "FORBIDDEN");
        assertThat(service.choose(s1, g.id(), ok.id()).slot().id()).isEqualTo(ok.id());
        GroupDto g2 = service.createGroup(admin, new GroupRequest("Groupe 2", List.of(s2.id()), t3.id()));
        assertCode(() -> service.choose(s2, g2.id(), ok.id()), "SLOT_TAKEN");
        assertCode(() -> service.delete(admin, ok.id()), "SLOT_TAKEN");
        assertCode(() -> service.update(admin, ok.id(), new SlotRequest(day.plus(2, ChronoUnit.HOURS), 40, "A1", List.of(t1.id(), t2.id()))), "SUPERVISOR_IN_JURY");
        jdbc.update("update defense_slots set start_at = now() + interval '10 hours' where id = ?", ok.id());
        SlotDto later = slot(4, "C3", t2, t3);
        assertCode(() -> service.choose(s1, g.id(), later.id()), "TOO_LATE");
    }

    @Test
    void concurrentChoicesOfTheSameSlotOnlyOneSucceeds() throws Exception {
        GroupDto g1 = service.createGroup(admin, new GroupRequest("G1", List.of(s1.id()), t3.id()));
        GroupDto g2 = service.createGroup(admin, new GroupRequest("G2", List.of(s2.id()), t3.id()));
        SlotDto s = slot(0, "A1", t1, t2);
        Callable<Boolean> a = () -> { try { service.choose(s1, g1.id(), s.id()); return true; } catch (Exception e) { return false; } };
        Callable<Boolean> b = () -> { try { service.choose(s2, g2.id(), s.id()); return true; } catch (Exception e) { return false; } };
        var pool = Executors.newFixedThreadPool(2);
        var fa = pool.submit(a);
        var fb = pool.submit(b);
        int ok = (fa.get() ? 1 : 0) + (fb.get() ? 1 : 0);
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
    }

    @Test
    void finalGradeIsPublishedWhenTheWholeJuryHasGraded() {
        GroupDto g = service.createGroup(admin, new GroupRequest("Groupe 1", List.of(s1.id()), t3.id()));
        SlotDto s = slot(0, "A1", t1, t2);
        service.choose(s1, g.id(), s.id());
        assertCode(() -> service.grade(t1, s.id(), new GradeRequest(new BigDecimal("15"), "Bien")), "DEFENSE_NOT_DONE");
        jdbc.update("update defense_slots set start_at = now() - interval '2 hours' where id = ?", s.id());
        assertCode(() -> service.grade(t3, s.id(), new GradeRequest(new BigDecimal("15"), "Bien")), "FORBIDDEN");
        service.grade(t1, s.id(), new GradeRequest(new BigDecimal("15.5"), "Démo claire"));
        ResultDto partial = service.result(s1, g.id());
        assertThat(partial.published()).isFalse();
        assertThat(partial.finalGrade()).isNull();
        service.grade(t2, s.id(), new GradeRequest(new BigDecimal("12"), "Tests à compléter"));
        ResultDto full = service.result(s1, g.id());
        assertThat(full.published()).isTrue();
        assertThat(full.finalGrade()).isEqualByComparingTo("13.75");
        assertCode(() -> service.result(s2, g.id()), "NOT_FOUND");
        assertThatThrownBy(() -> service.grade(t1, s.id(), new GradeRequest(new BigDecimal("15.1"), "x"))).isInstanceOf(ValidationException.class);
    }
}
