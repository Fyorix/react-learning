package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.timetable.dto.TimetableDtos.*;
import fr.epita.platform.apps.timetable.service.TimetableService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.IsoWeeks;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimetableServiceTest extends IntegrationTest {

    static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Autowired
    TimetableService service;
    @Autowired
    AppRoles roles;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser admin;
    AuthUser t1;
    AuthUser t2;
    AuthUser student;
    RoomDto amphi;
    RoomDto small;
    GroupDto td1;
    GroupDto td2;
    SubjectDto react;
    SubjectDto java;
    LocalDate day;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        t1 = teacher("Prof Un");
        t2 = teacher("Prof Deux");
        student = auth(user("Étudiant"));
        amphi = service.createRoom(admin, new RoomRequest("Amphi", 100));
        small = service.createRoom(admin, new RoomRequest("Box", 10));
        td1 = service.createGroup(admin, new GroupRequest("TD1", 30, List.of(student.id())));
        td2 = service.createGroup(admin, new GroupRequest("TD2", 8, List.of()));
        react = service.createSubject(admin, new SubjectRequest("React", 4, td1.id()));
        java = service.createSubject(admin, new SubjectRequest("Java", 10, td2.id()));
        day = LocalDate.now(PARIS).plusWeeks(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private AuthUser teacher(String name) {
        AuthUser t = auth(user(name));
        roles.grant(TimetableService.ROLES, Set.of(TimetableService.TEACHER), t.id(), TimetableService.TEACHER);
        return t;
    }

    private Instant at(int h, int m) {
        return day.atTime(LocalTime.of(h, m)).atZone(PARIS).toInstant();
    }

    private CourseRequest course(SubjectDto s, AuthUser t, RoomDto r, int h1, int m1, int h2, int m2) {
        return new CourseRequest(s.id(), t.id(), r.id(), at(h1, m1), at(h2, m2));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void slotFormatRules() {
        assertThatThrownBy(() -> service.create(admin, course(react, t1, amphi, 7, 30, 9, 0))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(admin, course(react, t1, amphi, 9, 10, 10, 0))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(admin, course(react, t1, amphi, 9, 0, 9, 15))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(admin, course(react, t1, amphi, 19, 0, 21, 0))).isInstanceOf(ValidationException.class);
        assertThat(service.create(admin, course(react, t1, amphi, 18, 0, 20, 0)).groupName()).isEqualTo("TD1");
    }

    @Test
    void conflictsAreAllReportedByCheckAndFirstOneByCreate() {
        service.create(admin, course(react, t1, amphi, 9, 0, 11, 0));
        CheckDto check = service.check(admin, course(react, t1, amphi, 10, 0, 13, 0), null);
        assertThat(check.ok()).isFalse();
        assertThat(check.conflicts()).extracting(ConflictDto::code).containsExactly("ROOM_CONFLICT", "TEACHER_CONFLICT", "GROUP_CONFLICT", "HOURS_EXCEEDED");
        assertCode(() -> service.create(admin, course(java, t2, amphi, 10, 0, 11, 0)), "ROOM_CONFLICT");
        assertCode(() -> service.create(admin, course(java, t1, small, 10, 0, 11, 0)), "TEACHER_CONFLICT");
        assertCode(() -> service.create(admin, course(react, t2, small, 14, 0, 15, 0)), "ROOM_TOO_SMALL");
        assertThat(service.create(admin, course(java, t2, small, 9, 0, 11, 0)).roomName()).isEqualTo("Box");
        assertCode(() -> service.create(admin, course(react, t1, amphi, 14, 0, 17, 0)), "HOURS_EXCEEDED");
    }

    @Test
    void movingACourseIgnoresItselfAndPastCoursesAreFrozen() {
        CourseDto c = service.create(admin, course(react, t1, amphi, 9, 0, 11, 0));
        CourseDto moved = service.update(admin, c.id(), course(react, t1, amphi, 10, 0, 12, 0));
        assertThat(moved.start()).isEqualTo(at(10, 0));
        assertThat(service.check(admin, course(react, t1, amphi, 9, 0, 11, 0), c.id()).ok()).isTrue();
        jdbc.update("update timetable_courses set start_at = now() - interval '3 hours', end_at = now() - interval '1 hour' where id = ?", c.id());
        assertCode(() -> service.delete(admin, c.id()), "COURSE_IN_PAST");
        assertThat(service.hours(td1.id()).get(0).doneHours()).isEqualTo(2.0);
    }

    @Test
    void weeklyViews() {
        service.create(admin, course(react, t1, amphi, 9, 0, 11, 0));
        String week = IsoWeeks.of(day);
        assertThat(service.mine(student, week)).hasSize(1);
        assertThat(service.mine(t1, week)).hasSize(1);
        assertThat(service.mine(t2, week)).isEmpty();
        assertThat(service.week(week, null, null, amphi.id())).hasSize(1);
        assertThatThrownBy(() -> service.week(week, td1.id(), t1.id(), null)).isInstanceOf(ValidationException.class);
        assertThat(service.hours(td1.id()).get(0).scheduledHours()).isEqualTo(2.0);
    }
}
