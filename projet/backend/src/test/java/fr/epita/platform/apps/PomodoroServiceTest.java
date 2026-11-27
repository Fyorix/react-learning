package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.RoomRequest;
import fr.epita.platform.apps.pomodoro.dto.PomodoroDtos.StateDto;
import fr.epita.platform.apps.pomodoro.entity.Phase;
import fr.epita.platform.apps.pomodoro.service.PomodoroService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PomodoroServiceTest extends IntegrationTest {

    @Autowired
    PomodoroService service;

    AuthUser host;
    AuthUser bob;
    StateDto room;

    @BeforeEach
    void setUp() {
        host = auth(user("Hana Hôte"));
        bob = auth(user("Bob"));
        room = service.create(host, new RoomRequest("Focus", 25, 5, 15, 2));
        service.invite(host, room.roomId(), List.of(bob.id()));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    /** Fait comme si la phase courante avait commencé il y a `minutes` minutes. */
    private void shiftPhase(int minutes) {
        jdbc.update("update pomodoro_rooms set phase_started_at = phase_started_at - make_interval(mins => ?), "
                + "phase_ends_at = phase_ends_at - make_interval(mins => ?) where id = ?", minutes, minutes, room.roomId());
        jdbc.update("update pomodoro_members set present_since = present_since - make_interval(mins => ?) where room_id = ?",
                minutes, room.roomId());
    }

    @Test
    void onlyTheHostDrivesTheSession() {
        assertCode(() -> service.start(bob, room.roomId()), "FORBIDDEN");
        StateDto s = service.start(host, room.roomId());
        assertThat(s.phase()).isEqualTo(Phase.FOCUS);
        assertThat(Duration.between(s.serverTime(), s.phaseEndsAt()).toMinutes()).isBetween(24L, 25L);
        assertCode(() -> service.update(host, room.roomId(), new RoomRequest("x", 10, 5, 15, 4)), "SESSION_RUNNING");
        assertCode(() -> service.getState(auth(user("Eve")), room.roomId()), "FORBIDDEN");
    }

    @Test
    void pauseFreezesTheRemainingTime() {
        service.start(host, room.roomId());
        StateDto paused = service.pause(host, room.roomId());
        assertThat(paused.phase()).isEqualTo(Phase.PAUSED);
        long frozen = paused.remainingSeconds();
        assertThat(service.getState(bob, room.roomId()).remainingSeconds()).isEqualTo(frozen);
        StateDto resumed = service.resume(host, room.roomId());
        assertThat(resumed.phase()).isEqualTo(Phase.FOCUS);
        assertThat(resumed.remainingSeconds()).isBetween(frozen - 1, frozen);
    }

    @Test
    void phasesAdvanceFromTheEndTimeAndCreditPresentMembersOnly() {
        service.heartbeat(host, room.roomId());
        service.start(host, room.roomId());
        // la présence de l'hôte commence avant le focus ; Bob arrive en cours de route
        shiftPhase(24);
        service.heartbeat(host, room.roomId());
        service.heartbeat(bob, room.roomId());
        shiftPhase(2);
        StateDto s = service.heartbeat(host, room.roomId());
        assertThat(s.phase()).isEqualTo(Phase.SHORT_BREAK);
        assertThat(s.cycle()).isEqualTo(1);
        assertThat(service.stats(host, "week").total()).isEqualTo(1);
        assertThat(service.stats(bob, "week").total()).isZero();
    }

    @Test
    void longBreakAfterTheConfiguredNumberOfFocusAndSkipDoesNotCredit() {
        service.start(host, room.roomId());
        service.skip(host, room.roomId());            // focus 1 interrompu → pause courte
        assertThat(service.getState(host, room.roomId()).phase()).isEqualTo(Phase.SHORT_BREAK);
        service.skip(host, room.roomId());            // → focus 2
        StateDto s = service.skip(host, room.roomId()); // focus 2 → pause longue (2 cycles)
        assertThat(s.phase()).isEqualTo(Phase.LONG_BREAK);
        assertThat(service.stats(host, "week").total()).isZero();
        assertThat(service.stop(host, room.roomId()).phase()).isEqualTo(Phase.IDLE);
    }

    @Test
    void roomWithoutPresentMemberForTenMinutesGoesIdle() {
        service.heartbeat(host, room.roomId());
        service.start(host, room.roomId());
        jdbc.update("update pomodoro_members set last_seen_at = ? where room_id = ?",
                Timestamp.from(Instant.now().minus(Duration.ofMinutes(11))), room.roomId());
        assertThat(service.getState(bob, room.roomId()).phase()).isEqualTo(Phase.IDLE);
    }
}
