package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.oncall.dto.OnCallDtos.*;
import fr.epita.platform.apps.oncall.entity.SwapStatus;
import fr.epita.platform.apps.oncall.service.OnCallService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.IsoWeeks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OnCallServiceTest extends IntegrationTest {

    @Autowired
    OnCallService service;

    AuthUser admin;
    AuthUser manager;
    AuthUser a;
    AuthUser b;
    AuthUser c;
    TeamDto team;
    LocalDate next;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        manager = auth(user("Maya Manager"));
        a = auth(user("Alice"));
        b = auth(user("Bob"));
        c = auth(user("Chloé"));
        team = service.create(admin, new TeamRequest("Exploitation", manager.id(), List.of(a.id(), b.id(), c.id())));
        next = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private String w(int offset) {
        return IsoWeeks.of(next.plusWeeks(offset));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void rotationIsFairWithoutConsecutiveWeeksAndRespectsUnavailability() {
        service.unavailable(a, team.id(), new UnavailabilityRequest(next.plusWeeks(1), "Congés"));
        ScheduleDto s = service.generate(manager, team.id(), new GenerateRequest(w(0), 9, false));
        assertThat(s.weeks()).hasSize(9).allMatch(x -> x.userId() != null);
        for (int i = 1; i < 9; i++) {
            assertThat(s.weeks().get(i).userId()).isNotEqualTo(s.weeks().get(i - 1).userId());
        }
        assertThat(s.weeks().get(1).userId()).isNotEqualTo(a.id());
        Map<UUID, Long> counts = s.weeks().stream().collect(Collectors.groupingBy(WeekDto::userId, Collectors.counting()));
        assertThat(counts.values()).containsOnly(3L);
        assertCode(() -> service.generate(a, team.id(), new GenerateRequest(w(0), 4, false)), "FORBIDDEN");
        assertCode(() -> service.generate(manager, team.id(), new GenerateRequest(IsoWeeks.of(LocalDate.now()), 4, false)), "WEEK_LOCKED");
    }

    @Test
    void manualAssignmentChecksLockAvailabilityAndConsecutiveWeeks() {
        ScheduleDto s = service.generate(manager, team.id(), new GenerateRequest(w(0), 3, false));
        UUID first = s.weeks().get(0).userId();
        assertCode(() -> service.assign(manager, team.id(), w(1), first), "CONSECUTIVE_ON_CALL");
        assertCode(() -> service.assign(manager, team.id(), IsoWeeks.of(LocalDate.now()), first), "WEEK_LOCKED");
        AuthUser other = List.of(a, b, c).stream().filter(x -> !x.id().equals(first) && !x.id().equals(s.weeks().get(1).userId())).findFirst().orElseThrow();
        service.unavailable(other, team.id(), new UnavailabilityRequest(next.plusWeeks(5), "Formation"));
        assertCode(() -> service.assign(manager, team.id(), w(5), other.id()), "MEMBER_UNAVAILABLE");
    }

    @Test
    void swapNeedsColleagueThenManagerAndRespectsRules() {
        service.assign(manager, team.id(), w(0), a.id());
        service.assign(manager, team.id(), w(1), b.id());
        service.assign(manager, team.id(), w(2), c.id());
        service.assign(manager, team.id(), w(3), b.id());
        SwapDto req = service.requestSwap(a, new SwapRequest(team.id(), w(0), w(2), c.id()));
        assertCode(() -> service.approve(manager, req.id()), "INVALID_STATUS_TRANSITION");
        assertCode(() -> service.accept(a, req.id()), "FORBIDDEN");
        assertThat(service.accept(c, req.id()).status()).isEqualTo(SwapStatus.ACCEPTED);
        // pas encore effectif
        assertThat(service.schedule(manager, team.id(), w(0), w(0)).weeks().get(0).userId()).isEqualTo(a.id());
        assertThat(service.list(manager, "to-approve")).hasSize(1);
        assertCode(() -> service.approve(a, req.id()), "FORBIDDEN");
        service.approve(manager, req.id());
        assertThat(service.schedule(manager, team.id(), w(0), w(2)).weeks()).extracting(WeekDto::userId)
                .containsExactly(c.id(), b.id(), a.id());
        assertCode(() -> service.reject(manager, req.id()), "INVALID_STATUS_TRANSITION");
    }

    @Test
    void swapCreatingConsecutiveWeeksIsRefused() {
        service.assign(manager, team.id(), w(0), a.id());
        service.assign(manager, team.id(), w(1), b.id());
        service.assign(manager, team.id(), w(2), c.id());
        service.assign(manager, team.id(), w(3), a.id());
        service.assign(manager, team.id(), w(4), b.id());
        // planning a, b, c, a, b : a récupérerait la semaine 1 juste après sa semaine 0
        assertCode(() -> service.requestSwap(b, new SwapRequest(team.id(), w(1), w(3), a.id())), "CONSECUTIVE_ON_CALL");
        // b récupérerait la semaine 2 juste après sa semaine 1
        assertCode(() -> service.requestSwap(c, new SwapRequest(team.id(), w(2), w(4), b.id())), "CONSECUTIVE_ON_CALL");
        assertCode(() -> service.requestSwap(a, new SwapRequest(team.id(), w(1), w(2), c.id())), "NOT_ON_CALL");
        assertThat(service.stats(manager, team.id(), null)).isNotEmpty();
    }

    @Test
    void twoMemberTeamMayHaveConsecutiveWeeksWhenUnavoidable() {
        TeamDto duo = service.create(admin, new TeamRequest("Duo", manager.id(), List.of(a.id(), b.id())));
        service.unavailable(b, duo.id(), new UnavailabilityRequest(next.plusWeeks(1), "Congés"));
        ScheduleDto s = service.generate(manager, duo.id(), new GenerateRequest(w(0), 2, false));
        assertThat(s.weeks()).extracting(WeekDto::userId).containsExactly(a.id(), a.id());

    }
}
