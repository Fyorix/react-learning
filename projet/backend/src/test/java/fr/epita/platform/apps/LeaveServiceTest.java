package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.leave.dto.LeaveDtos.BalanceRequest;
import fr.epita.platform.apps.leave.dto.LeaveDtos.LeaveDto;
import fr.epita.platform.apps.leave.dto.LeaveDtos.LeaveRequestBody;
import fr.epita.platform.apps.leave.entity.LeaveEnums.Half;
import fr.epita.platform.apps.leave.entity.LeaveEnums.LeaveStatus;
import fr.epita.platform.apps.leave.entity.LeaveEnums.LeaveType;
import fr.epita.platform.apps.leave.service.LeaveService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeaveServiceTest extends IntegrationTest {

    @Autowired
    LeaveService service;
    @Autowired
    ManagerRelations relations;

    AuthUser admin;
    AuthUser marc;
    AuthUser alice;
    int year;
    LocalDate monday;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        marc = auth(user("Marc Manager"));
        alice = auth(user("Alice"));
        relations.assign(LeaveService.REPORTS, alice.id(), marc.id());
        year = LocalDate.now().getYear() + 1;
        service.setBalance(admin, alice.id(), new BalanceRequest(year, new BigDecimal("5"), new BigDecimal("2")));
        // un lundi ordinaire de l'année suivante (évite les jours fériés de début d'année)
        monday = LocalDate.of(year, 3, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        if (monday.plusDays(4).getMonthValue() != 3) {
            monday = monday.plusWeeks(1);
        }
    }

    private LeaveRequestBody req(LeaveType t, LocalDate s, LocalDate e, Half sh, Half eh) {
        return new LeaveRequestBody(t, s, e, sh, eh, null);
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void daysExcludeWeekendsHolidaysAndCountHalfDays() {
        assertThat(LeaveService.countDays(monday, monday.plusDays(6), Half.MORNING, Half.AFTERNOON)).isEqualByComparingTo("5");
        assertThat(LeaveService.countDays(monday, monday.plusDays(1), Half.AFTERNOON, Half.MORNING)).isEqualByComparingTo("1");
        LocalDate xmas = LocalDate.of(2026, 12, 21);
        assertThat(LeaveService.countDays(xmas, xmas.plusDays(4), Half.MORNING, Half.AFTERNOON)).isEqualByComparingTo("4");
    }

    @Test
    void balanceCannotBeExceededAndPendingDaysAreDeducted() {
        service.request(alice, req(LeaveType.PAID, monday, monday.plusDays(2), Half.MORNING, Half.AFTERNOON));
        assertThat(service.balances(alice, year).balances().get(0).remaining()).isEqualByComparingTo("2");
        assertCode(() -> service.request(alice, req(LeaveType.PAID, monday.plusWeeks(1), monday.plusWeeks(1).plusDays(2), Half.MORNING, Half.AFTERNOON)), "INSUFFICIENT_BALANCE");
        // sans solde : pas de contrôle
        service.request(alice, req(LeaveType.UNPAID, monday.plusWeeks(2), monday.plusWeeks(2).plusDays(4), Half.MORNING, Half.AFTERNOON));
    }

    @Test
    void requestsCannotOverlapButHalfDaysCanTouch() {
        service.request(alice, req(LeaveType.RTT, monday, monday, Half.MORNING, Half.MORNING));
        service.request(alice, req(LeaveType.RTT, monday, monday, Half.AFTERNOON, Half.AFTERNOON));
        assertCode(() -> service.request(alice, req(LeaveType.UNPAID, monday, monday.plusDays(1), Half.MORNING, Half.AFTERNOON)), "LEAVE_OVERLAP");
    }

    @Test
    void onlyTheManagerDecidesAndNeverForHimself() {
        LeaveDto d = service.request(alice, req(LeaveType.PAID, monday, monday, Half.MORNING, Half.AFTERNOON));
        AuthUser bob = auth(user("Bob"));
        assertCode(() -> service.approve(bob, d.id()), "FORBIDDEN");
        assertCode(() -> service.approve(alice, d.id()), "FORBIDDEN");
        assertThatThrownBy(() -> service.reject(marc, d.id(), " ")).isInstanceOf(RuntimeException.class);
        assertThat(service.approve(marc, d.id()).status()).isEqualTo(LeaveStatus.APPROVED);
        assertCode(() -> service.approve(marc, d.id()), "INVALID_STATUS_TRANSITION");
        assertThat(service.balances(alice, year).balances().get(0).taken()).isEqualByComparingTo("1");
    }

    @Test
    void cancellingAnApprovedLeaveBeforeItStartsRestoresTheBalance() {
        LeaveDto d = service.request(alice, req(LeaveType.PAID, monday, monday.plusDays(1), Half.MORNING, Half.AFTERNOON));
        service.approve(marc, d.id());
        service.cancel(alice, d.id());
        assertThat(service.balances(alice, year).balances().get(0).remaining()).isEqualByComparingTo("5");

        LeaveDto started = service.request(alice, req(LeaveType.UNPAID, LocalDate.now().minusDays(1), LocalDate.now().plusDays(1), Half.MORNING, Half.AFTERNOON));
        service.approve(marc, started.id());
        assertCode(() -> service.cancel(alice, started.id()), "LEAVE_STARTED");
    }

    @Test
    void invalidPeriodsAreRejected() {
        assertThatThrownBy(() -> service.request(alice, req(LeaveType.PAID, monday.plusDays(5), monday.plusDays(6), Half.MORNING, Half.AFTERNOON)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.request(alice, req(LeaveType.PAID, monday, monday, Half.AFTERNOON, Half.MORNING)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void teamCalendarHidesTheTypeFromColleagues() {
        AuthUser bob = auth(user("Bob"));
        relations.assign(LeaveService.REPORTS, bob.id(), marc.id());
        LeaveDto d = service.request(alice, req(LeaveType.RTT, monday, monday, Half.MORNING, Half.AFTERNOON));
        service.approve(marc, d.id());
        var month = java.time.YearMonth.from(monday);
        assertThat(service.teamCalendar(bob, month)).singleElement().extracting("type").isNull();
        assertThat(service.teamCalendar(marc, month)).singleElement().extracting("type").isEqualTo(LeaveType.RTT);
    }
}
