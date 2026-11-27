package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.training.dto.TrainingDtos.CourseDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.CourseRequest;
import fr.epita.platform.apps.training.dto.TrainingDtos.EnrollmentDto;
import fr.epita.platform.apps.training.dto.TrainingDtos.SessionRequest;
import fr.epita.platform.apps.training.entity.EnrollmentStatus;
import fr.epita.platform.apps.training.service.TrainingService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.relation.ManagerRelations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrainingServiceTest extends IntegrationTest {

    @Autowired
    TrainingService service;
    @Autowired
    ManagerRelations relations;

    AuthUser admin;
    AuthUser trainer;
    AuthUser marc;
    AuthUser alice;
    AuthUser bob;
    UUID sessionId;
    Instant start = Instant.now().plus(Duration.ofDays(10));

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        trainer = auth(user("Tess Formatrice"));
        marc = auth(user("Marc"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        relations.assign(TrainingService.REPORTS, alice.id(), marc.id());
        relations.assign(TrainingService.REPORTS, bob.id(), marc.id());
        CourseDto c = service.createCourse(admin, new CourseRequest("React", "Dev", trainer.id(), new BigDecimal("7"), "Bases"));
        c = service.addSession(admin, c.id(), new SessionRequest(start, start.plus(Duration.ofHours(7)), "Salle A", 1));
        sessionId = c.sessions().get(0).id();
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void managerApprovalConfirmsThenWaitlists() {
        EnrollmentDto a = service.enroll(alice, sessionId);
        EnrollmentDto b = service.enroll(bob, sessionId);
        assertThat(a.status()).isEqualTo(EnrollmentStatus.REQUESTED);
        assertCode(() -> service.approve(trainer, a.id()), "FORBIDDEN");
        assertThat(service.approve(marc, a.id()).status()).isEqualTo(EnrollmentStatus.CONFIRMED);
        EnrollmentDto waiting = service.approve(marc, b.id());
        assertThat(waiting.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
        assertThat(waiting.waitlistPosition()).isEqualTo(1);
        assertCode(() -> service.approve(marc, b.id()), "INVALID_STATUS_TRANSITION");
    }

    @Test
    void cancellingAConfirmedSeatPromotesTheFirstWaitlisted() {
        service.approve(marc, service.enroll(alice, sessionId).id());
        service.approve(marc, service.enroll(bob, sessionId).id());
        service.cancel(alice, sessionId);
        assertThat(service.mine(bob).get(0).status()).isEqualTo(EnrollmentStatus.CONFIRMED);
    }

    @Test
    void duplicatesOverlapsAndLateCancellationsAreRefused() {
        service.enroll(alice, sessionId);
        assertCode(() -> service.enroll(alice, sessionId), "ALREADY_ENROLLED");
        CourseDto other = service.createCourse(admin, new CourseRequest("Java", "Dev", trainer.id(), new BigDecimal("3"), "x"));
        other = service.addSession(admin, other.id(), new SessionRequest(start.plus(Duration.ofHours(2)), start.plus(Duration.ofHours(5)), "Salle B", 5));
        UUID overlapping = other.sessions().get(0).id();
        assertCode(() -> service.enroll(alice, overlapping), "SESSION_OVERLAP");
        jdbc.update("update training_sessions set starts_at = ? where id = ?", Timestamp.from(Instant.now().plus(Duration.ofHours(24))), sessionId);
        assertCode(() -> service.cancel(alice, sessionId), "TOO_LATE_TO_CANCEL");
    }

    @Test
    void attendanceAfterTheSessionAndCertificateForPresentOnly() {
        EnrollmentDto a = service.approve(marc, service.enroll(alice, sessionId).id());
        assertCode(() -> service.attendance(trainer, sessionId, List.of(alice.id())), "SESSION_NOT_DONE");
        jdbc.update("update training_sessions set starts_at = ?, ends_at = ? where id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofHours(8))), Timestamp.from(Instant.now().minus(Duration.ofHours(1))), sessionId);
        assertCode(() -> service.attendance(alice, sessionId, List.of(alice.id())), "FORBIDDEN");
        service.attendance(trainer, sessionId, List.of(alice.id()));
        assertThat(service.certificate(alice, a.id()).hours()).isEqualByComparingTo("7");
        assertThat(service.stats(admin, null).hoursByEmployee()).extracting("userName").containsExactly("Alice");
    }
}
