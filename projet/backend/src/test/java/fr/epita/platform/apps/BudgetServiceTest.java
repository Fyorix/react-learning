package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.budget.dto.BudgetDtos.*;
import fr.epita.platform.apps.budget.entity.BudgetCategory;
import fr.epita.platform.apps.budget.entity.PurchaseStatus;
import fr.epita.platform.apps.budget.service.BudgetService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetServiceTest extends IntegrationTest {

    @Autowired
    BudgetService service;

    AuthUser admin;
    AuthUser manager;
    AuthUser alice;
    AuthUser outsider;
    TeamDto team;
    int year;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        manager = auth(user("Maya Manager"));
        alice = auth(user("Alice"));
        outsider = auth(user("Oscar"));
        team = service.createTeam(admin, new TeamRequest("Data " + System.nanoTime(), manager.id(), List.of(alice.id())));
        year = LocalDate.now().getYear();
        service.setEnvelopes(admin, team.id(), new EnvelopesRequest(year, List.of(new EnvelopeItem(BudgetCategory.EQUIPMENT, new BigDecimal("1000.00")))));
    }

    private PurchaseDto ask(String amount) {
        return service.create(alice, new PurchaseRequestBody(team.id(), BudgetCategory.EQUIPMENT, "Écran", new BigDecimal(amount), null));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void approvalCommitsAndSpendingReplacesTheCommitment() {
        PurchaseDto r = ask("600.00");
        assertCode(() -> service.approve(alice, r.id(), null), "FORBIDDEN");
        service.approve(manager, r.id(), null);
        CategoryDto eq = service.dashboard(alice, team.id(), year).categories().stream().filter(c -> c.category() == BudgetCategory.EQUIPMENT).findFirst().orElseThrow();
        assertThat(eq.committed()).isEqualByComparingTo("600.00");
        assertThat(eq.remaining()).isEqualByComparingTo("400.00");
        service.spend(manager, r.id(), new SpendRequest(new BigDecimal("550.00"), LocalDate.now()));
        CategoryDto after = service.dashboard(alice, team.id(), year).categories().stream().filter(c -> c.category() == BudgetCategory.EQUIPMENT).findFirst().orElseThrow();
        assertThat(after.committed()).isEqualByComparingTo("0.00");
        assertThat(after.spent()).isEqualByComparingTo("550.00");
        assertThat(after.remaining()).isEqualByComparingTo("450.00");
        assertCode(() -> service.dashboard(outsider, team.id(), year), "NOT_FOUND");
    }

    @Test
    void exceedingTheRemainingNeedsAnAdminOverride() {
        service.approve(manager, ask("700.00").id(), null);
        PurchaseDto big = ask("400.00");
        assertCode(() -> service.approve(manager, big.id(), null), "BUDGET_EXCEEDED");
        assertCode(() -> service.approve(manager, big.id(), new ApproveRequest(true, "urgent")), "FORBIDDEN");
        assertThatThrownBy(() -> service.approve(admin, big.id(), new ApproveRequest(true, " "))).isInstanceOf(ValidationException.class);
        PurchaseDto ok = service.approve(admin, big.id(), new ApproveRequest(true, "Remplacement d'un PC en panne"));
        assertThat(ok.overrideReason()).isEqualTo("Remplacement d'un PC en panne");
        List<AlertDto> alerts = service.alerts(manager);
        assertThat(alerts).singleElement().satisfies(a -> assertThat(a.level()).isEqualTo(100));
    }

    @Test
    void alertsAtEightyPercentAndCancellationRules() {
        PurchaseDto r = ask("850.00");
        service.approve(manager, r.id(), null);
        assertThat(service.alerts(admin)).singleElement().satisfies(a -> assertThat(a.level()).isEqualTo(80));
        PurchaseDto pending = ask("10.00");
        assertCode(() -> service.cancel(manager, pending.id()), "FORBIDDEN");
        service.cancel(alice, pending.id());
        assertCode(() -> service.cancel(alice, r.id()), "INVALID_STATUS_TRANSITION");
        assertThat(service.list(manager, team.id(), PurchaseStatus.CANCELLED)).hasSize(1);
    }

    @Test
    void closedFiscalYearRefusesRequestsAndSpending() {
        PurchaseDto r = ask("100.00");
        service.approve(manager, r.id(), null);
        service.closeYear(admin, year);
        assertCode(() -> ask("50.00"), "FISCAL_YEAR_CLOSED");
        assertCode(() -> service.spend(manager, r.id(), new SpendRequest(new BigDecimal("90.00"), LocalDate.now())), "FISCAL_YEAR_CLOSED");
        assertCode(() -> service.closeYear(admin, year), "FISCAL_YEAR_CLOSED");
    }
}
