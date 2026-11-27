package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.crm.dto.CrmDtos.*;
import fr.epita.platform.apps.crm.entity.AccountSize;
import fr.epita.platform.apps.crm.entity.InteractionType;
import fr.epita.platform.apps.crm.entity.PipelineStage;
import fr.epita.platform.apps.crm.service.CrmService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CrmServiceTest extends IntegrationTest {

    @Autowired
    CrmService service;
    @Autowired
    AppRoles roles;

    AuthUser admin;
    AuthUser sam;
    AuthUser lea;
    AuthUser outsider;
    AccountDto acme;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        sam = sales("Sam Sales");
        lea = sales("Léa Sales");
        outsider = auth(user("Hors CRM"));
        acme = service.createAccount(sam, new AccountRequest("Acme", "Industrie", AccountSize.ETI, null));
    }

    private AuthUser sales(String name) {
        AuthUser u = auth(user(name));
        roles.grant(CrmService.ROLES, Set.of(CrmService.SALES), u.id(), CrmService.SALES);
        return u;
    }

    private OpportunityDto opp(AuthUser who, String amount, LocalDate close) {
        return service.createOpportunity(who, new OpportunityRequest(acme.id(), "Deal " + amount, new BigDecimal(amount), close));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void accountsContactsAndAccess() {
        assertCode(() -> service.accounts(outsider, null, null), "FORBIDDEN");
        service.addContact(sam, acme.id(), new ContactRequest("Claire", "Durand", "Claire@Acme.example", null, "Achats"));
        assertCode(() -> service.addContact(lea, acme.id(), new ContactRequest("C", "D", "claire@acme.example", null, "Achats")), "CONTACT_EXISTS");
        assertCode(() -> service.updateAccount(lea, acme.id(), new AccountRequest("Acme 2", "Industrie", AccountSize.GE, null)), "FORBIDDEN");
        assertCode(() -> service.createAccount(sam, new AccountRequest("Beta", "Retail", AccountSize.PME, lea.id())), "FORBIDDEN");
        assertThat(service.createAccount(admin, new AccountRequest("Beta", "Retail", AccountSize.PME, lea.id())).ownerName()).isEqualTo("Léa Sales");
        assertThat(service.accounts(sam, "acm", null)).singleElement().satisfies(a -> assertThat(a.name()).isEqualTo("Acme"));
        assertThat(service.account(lea, acme.id()).contacts()).hasSize(1);
        assertThat(service.salesPeople(sam)).contains(sam.id(), lea.id()).doesNotContain(outsider.id());
    }

    @Test
    void stageTransitions() {
        OpportunityDto o = opp(sam, "1000", LocalDate.now().plusMonths(1));
        assertCode(() -> service.changeStage(lea, o.id(), new StageRequest(PipelineStage.PROPOSAL, null)), "FORBIDDEN");
        assertCode(() -> service.changeStage(sam, o.id(), new StageRequest(PipelineStage.NEGOTIATION, null)), "INVALID_STAGE_TRANSITION");
        assertThat(service.changeStage(sam, o.id(), new StageRequest(PipelineStage.PROPOSAL, null)).probability()).isEqualTo(40);
        assertThat(service.changeStage(sam, o.id(), new StageRequest(PipelineStage.QUALIFICATION, null)).stage()).isEqualTo(PipelineStage.QUALIFICATION);
        assertThatThrownBy(() -> service.changeStage(sam, o.id(), new StageRequest(PipelineStage.LOST, " "))).isInstanceOf(ValidationException.class);
        assertThat(service.changeStage(admin, o.id(), new StageRequest(PipelineStage.LOST, "Budget gelé")).lostReason()).isEqualTo("Budget gelé");
        assertCode(() -> service.changeStage(sam, o.id(), new StageRequest(PipelineStage.PROPOSAL, null)), "OPPORTUNITY_CLOSED");
        assertCode(() -> service.updateOpportunity(sam, o.id(), new OpportunityRequest(acme.id(), "x", BigDecimal.ONE, LocalDate.now())), "OPPORTUNITY_CLOSED");
        assertThat(service.opportunity(sam, opp(sam, "10", LocalDate.now()).id()).allowedStages()).containsExactly(PipelineStage.PROPOSAL, PipelineStage.WON, PipelineStage.LOST);
    }

    @Test
    void weightedForecastByMonth() {
        LocalDate next = LocalDate.now().plusMonths(1).withDayOfMonth(10);
        opp(sam, "10000", next);
        OpportunityDto b = opp(sam, "20000", next);
        service.changeStage(sam, b.id(), new StageRequest(PipelineStage.PROPOSAL, null));
        OpportunityDto won = opp(lea, "50000", next);
        service.changeStage(lea, won.id(), new StageRequest(PipelineStage.WON, null));
        opp(lea, "1000", LocalDate.now().minusMonths(2));
        ForecastDto f = service.forecast(sam, 3, null);
        ForecastMonth m = f.months().stream().filter(x -> x.month().equals(YearMonth.from(next).toString())).findFirst().orElseThrow();
        assertThat(m.weighted()).isEqualByComparingTo("9000");
        assertThat(m.opportunities()).isEqualTo(2);
        assertThat(f.overdueWeighted()).isEqualByComparingTo("100");
        assertThat(service.forecast(sam, 3, lea.id()).total()).isEqualByComparingTo("0");
        assertThat(service.pipeline(sam, null, null)).hasSize(5).filteredOn(c -> c.stage() == PipelineStage.WON).singleElement()
                .satisfies(c -> assertThat(c.total()).isEqualByComparingTo("50000"));
    }

    @Test
    void interactionsAndTasks() {
        OpportunityDto o = opp(sam, "5000", LocalDate.now().plusDays(20));
        service.addInteraction(lea, new InteractionRequest(null, o.id(), InteractionType.CALL, LocalDate.now(), "Appel de découverte"));
        assertThatThrownBy(() -> service.addInteraction(sam, new InteractionRequest(acme.id(), null, InteractionType.EMAIL, LocalDate.now().plusDays(1), "Futur")))
                .isInstanceOf(ValidationException.class);
        assertThat(service.account(sam, acme.id()).interactions()).singleElement().satisfies(i -> assertThat(i.opportunityTitle()).isEqualTo("Deal 5000"));
        TaskDto late = service.createTask(sam, new TaskRequest(LocalDate.now().minusDays(2), "Relancer Claire", null, o.id()));
        service.createTask(sam, new TaskRequest(LocalDate.now(), "Envoyer la proposition", acme.id(), null));
        service.createTask(sam, new TaskRequest(LocalDate.now().plusDays(3), "Point mi-parcours", null, null));
        assertThat(service.myTasks(sam, "overdue")).singleElement().satisfies(t -> assertThat(t.accountName()).isEqualTo("Acme"));
        assertThat(service.myTasks(sam, "today")).hasSize(1);
        assertThat(service.myTasks(sam, "week")).hasSize(2);
        assertCode(() -> service.patchTask(lea, late.id(), true), "NOT_FOUND");
        service.patchTask(sam, late.id(), true);
        assertThat(service.myTasks(sam, "overdue")).isEmpty();
    }
}
