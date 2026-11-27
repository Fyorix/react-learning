package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.goals.dto.GoalDtos.GoalDto;
import fr.epita.platform.apps.goals.dto.GoalDtos.GoalRequest;
import fr.epita.platform.apps.goals.dto.GoalDtos.ReviewDecision;
import fr.epita.platform.apps.goals.dto.GoalDtos.ReviewRequest;
import fr.epita.platform.apps.goals.entity.GoalStatus;
import fr.epita.platform.apps.goals.service.GoalService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalServiceTest extends IntegrationTest {

    @Autowired
    GoalService service;

    AuthUser manager;
    AuthUser employee;
    AuthUser other;

    @BeforeEach
    void setUp() {
        manager = auth(user("Manager"));
        employee = auth(user("Salarie"));
        other = auth(user("Autre"));
        service.assignManager(employee.id(), manager.id());
    }

    private GoalRequest request(String title, LocalDate due) {
        return new GoalRequest(employee.id(), title, "Réduire le temps de réponse du support", "Tickets résolus",
                "tickets", BigDecimal.ZERO, BigDecimal.valueOf(50), due);
    }

    private GoalDto goal() {
        return service.create(manager, request("Résoudre 50 tickets", LocalDate.now().plusMonths(3)));
    }

    private void assertCode(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void smartRulesAreEnforced() {
        assertThatThrownBy(() -> service.create(manager, request("Objectif", LocalDate.now().plusMonths(13))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(manager, request("Objectif", LocalDate.now().minusDays(1))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(manager, new GoalRequest(employee.id(), "Objectif", "d", "m", "u",
                BigDecimal.TEN, BigDecimal.TEN, LocalDate.now().plusMonths(1))))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void onlyTheManagerCreatesGoalsAndAtMostFiveInProgress() {
        assertCode(() -> service.create(other, request("Objectif interdit", LocalDate.now().plusMonths(1))), "FORBIDDEN");
        for (int i = 0; i < 5; i++) {
            service.create(manager, request("Objectif numéro " + i, LocalDate.now().plusMonths(1)));
        }
        assertCode(() -> service.create(manager, request("Objectif de trop", LocalDate.now().plusMonths(1))),
                "TOO_MANY_GOALS");
    }

    @Test
    void progressIsRecordedOnlyByTheEmployeeAndLocksTheGoal() {
        GoalDto g = goal();
        assertCode(() -> service.recordProgress(manager, g.id(), BigDecimal.ONE, null), "FORBIDDEN");
        GoalDto after = service.recordProgress(employee, g.id(), BigDecimal.valueOf(25), "mi-parcours");
        assertThat(after.progress()).isEqualTo(50);
        assertThat(after.history()).hasSize(1);

        assertCode(() -> service.update(manager, g.id(), request("Titre changé", g.dueDate())), "GOAL_LOCKED");
        assertThat(service.update(manager, g.id(), request(g.title(), g.dueDate().plusDays(10))).dueDate())
                .isEqualTo(g.dueDate().plusDays(10));
        assertCode(() -> service.delete(manager, g.id()), "GOAL_LOCKED");
    }

    @Test
    void reviewWorkflowRequiresACommentToRefuse() {
        GoalDto g = goal();
        assertCode(() -> service.review(manager, g.id(), new ReviewRequest(ReviewDecision.VALIDATED, null)),
                "INVALID_STATUS_TRANSITION");
        service.submit(employee, g.id());
        assertThatThrownBy(() -> service.review(manager, g.id(), new ReviewRequest(ReviewDecision.NOT_VALIDATED, " ")))
                .isInstanceOf(ValidationException.class);
        assertThat(service.review(manager, g.id(), new ReviewRequest(ReviewDecision.REOPEN, null)).status())
                .isEqualTo(GoalStatus.IN_PROGRESS);
        service.submit(employee, g.id());
        assertThat(service.review(manager, g.id(), new ReviewRequest(ReviewDecision.VALIDATED, "Bravo")).status())
                .isEqualTo(GoalStatus.VALIDATED);
    }

    @Test
    void overdueGoalsAreSubmittedAutomatically() {
        GoalDto g = goal();
        jdbc.update("update goals set due_date = ? where id = ?", LocalDate.now().minusDays(1), g.id());
        assertCode(() -> service.recordProgress(employee, g.id(), BigDecimal.ONE, null), "GOAL_CLOSED");
        assertThat(service.get(employee, g.id()).status()).isEqualTo(GoalStatus.SUBMITTED);
    }

    @Test
    void visibilityAndReportingRules() {
        GoalDto g = goal();
        assertCode(() -> service.get(other, g.id()), "FORBIDDEN");
        assertThat(service.team(manager, null, null)).hasSize(1);
        assertThat(service.team(other, null, null)).isEmpty();
        assertCode(() -> service.assignManager(manager.id(), employee.id()), "REPORTING_CYCLE");
        assertCode(() -> service.assignManager(manager.id(), manager.id()), "SELF_MANAGER");
    }
}
