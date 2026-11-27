package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.license.dto.LicenseDtos.*;
import fr.epita.platform.apps.license.entity.Billing;
import fr.epita.platform.apps.license.entity.LicenseRequestStatus;
import fr.epita.platform.apps.license.entity.SoftwareStatus;
import fr.epita.platform.apps.license.service.LicenseService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.relation.ManagerRelations;
import fr.epita.platform.user.dto.UserDtos.UpdateUserRequest;
import fr.epita.platform.user.entity.Role;
import fr.epita.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LicenseServiceTest extends IntegrationTest {

    @Autowired
    LicenseService service;
    @Autowired
    UserService userService;
    @Autowired
    ManagerRelations relations;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser admin;
    AuthUser alice;
    AuthUser bob;
    SoftwareDto figma;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        figma = service.create(admin, new SoftwareRequest("Figma", "Figma Inc.", 1, new BigDecimal("45.00"), Billing.MONTHLY, LocalDate.now().plusDays(20), null));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    private SoftwareRequest figmaWith(int seats, SoftwareStatus status) {
        return new SoftwareRequest("Figma", "Figma Inc.", seats, new BigDecimal("45.00"), Billing.MONTHLY, LocalDate.now().plusDays(20), status);
    }

    @Test
    void seatsAreNeverOverAssigned() {
        service.assign(admin, figma.id(), alice.id());
        assertCode(() -> service.assign(admin, figma.id(), alice.id()), "ALREADY_ASSIGNED");
        assertCode(() -> service.assign(admin, figma.id(), bob.id()), "NO_SEAT_LEFT");
        assertCode(() -> service.update(admin, figma.id(), figmaWith(0, null)), "SEATS_BELOW_ASSIGNED");
        assertThat(service.catalog(alice).get(0).mine()).isTrue();
    }

    @Test
    void concurrentAssignmentsOfTheLastSeatOnlyOneSucceeds() throws Exception {
        Callable<Boolean> a = () -> { try { service.assign(admin, figma.id(), alice.id()); return true; } catch (ApiException e) { return false; } };
        Callable<Boolean> b = () -> { try { service.assign(admin, figma.id(), bob.id()); return true; } catch (ApiException e) { return false; } };
        var pool = Executors.newFixedThreadPool(2);
        var fa = pool.submit(a);
        var fb = pool.submit(b);
        int ok = (fa.get() ? 1 : 0) + (fb.get() ? 1 : 0);
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
    }

    @Test
    void requestsAreApprovedIntoSeatsAndTerminatedSoftwareRefusesThem() {
        LicenseRequestDto r = service.request(alice, new AskLicenseRequest(figma.id(), "Maquettes"));
        assertCode(() -> service.request(alice, new AskLicenseRequest(figma.id(), "encore")), "REQUEST_PENDING");
        assertThat(service.approve(admin, r.id()).status()).isEqualTo(LicenseRequestStatus.APPROVED);
        assertThat(service.mine(alice)).hasSize(1);
        LicenseRequestDto rb = service.request(bob, new AskLicenseRequest(figma.id(), "Moi aussi"));
        assertCode(() -> service.approve(admin, rb.id()), "NO_SEAT_LEFT");
        service.update(admin, figma.id(), figmaWith(2, SoftwareStatus.TERMINATED));
        assertCode(() -> service.request(bob, new AskLicenseRequest(figma.id(), "x")), "SOFTWARE_TERMINATED");
    }

    @Test
    void disablingAUserReleasesTheirSeats() {
        service.assign(admin, figma.id(), alice.id());
        LicenseRequestDto pending = service.request(alice, new AskLicenseRequest(
                service.create(admin, new SoftwareRequest("Slack", "Salesforce", 5, new BigDecimal("80.00"), Billing.YEARLY, LocalDate.now().plusMonths(6), null)).id(), "Chat"));
        userService.update(alice.id(), new UpdateUserRequest(alice.email(), "Alice", Role.USER, false));
        assertThat(service.holders(admin, figma.id())).isEmpty();
        assertThat(service.requests(admin, null).stream().filter(x -> x.id().equals(pending.id())).findFirst().orElseThrow().status())
                .isEqualTo(LicenseRequestStatus.REJECTED);
        assertThat(service.catalog(admin).stream().filter(s -> s.name().equals("Figma")).findFirst().orElseThrow().seatsLeft()).isEqualTo(1);
    }

    @Test
    void alertsAndMonthlyCosts() {
        relations.assign(LicenseService.REPORTS, alice.id(), bob.id());
        service.assign(admin, figma.id(), alice.id());
        SoftwareDto slack = service.create(admin, new SoftwareRequest("Slack", "Salesforce", 5, new BigDecimal("96.00"), Billing.YEARLY, LocalDate.now().plusMonths(6), null));
        service.assign(admin, slack.id(), bob.id());
        jdbc.update("update license_seats set last_used_on = current_date - 100 where user_id = ?", bob.id());
        AlertsDto alerts = service.alerts(admin);
        assertThat(alerts.renewals()).extracting(SoftwareDto::name).containsExactly("Figma");
        assertThat(alerts.unusedSeats()).extracting(UnusedSeat::userName).containsExactly("Bob");
        CostsDto costs = service.costs(admin, null);
        // 45 + 96/12 = 53
        assertThat(costs.total()).isEqualByComparingTo("53.00");
        assertThat(costs.byTeam()).extracting(CostRow::label).contains("Équipe de Bob", "Sans équipe");
        service.declareUsage(bob, slack.id());
        assertThat(service.alerts(admin).unusedSeats()).isEmpty();
    }
}
