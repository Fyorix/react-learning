package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.farewell.dto.FarewellDtos.*;
import fr.epita.platform.apps.farewell.entity.Occasion;
import fr.epita.platform.apps.farewell.entity.PotStatus;
import fr.epita.platform.apps.farewell.service.FarewellService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FarewellServiceTest extends IntegrationTest {

    @Autowired
    FarewellService service;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser orga;
    AuthUser lea;
    AuthUser alice;
    AuthUser bob;
    AuthUser outsider;

    @BeforeEach
    void setUp() {
        orga = auth(user("Olivia"));
        lea = auth(user("Léa Partante"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        outsider = auth(user("Oscar"));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    private PotDto openPot() {
        PotDto p = service.create(orga, new IdentityStep(lea.id(), Occasion.LEAVING, "Départ de Léa"));
        service.goal(orga, p.id(), new GoalStep(new BigDecimal("50.00"), Instant.now().plus(3, ChronoUnit.DAYS)));
        service.invitees(orga, p.id(), new InviteesStep(List.of(alice.id(), bob.id())));
        return service.open(orga, p.id());
    }

    @Test
    void wizardValidatesStepByStepAndRefusesIncompletePublication() {
        assertThatThrownBy(() -> service.create(orga, new IdentityStep(orga.id(), Occasion.OTHER, "Moi"))).isInstanceOf(ValidationException.class);
        PotDto p = service.create(orga, new IdentityStep(lea.id(), Occasion.LEAVING, "Départ de Léa"));
        ReviewDto r = service.review(orga, p.id());
        assertThat(r.canOpen()).isFalse();
        assertThat(r.steps()).extracting(StepStatus::valid).containsExactly(true, false, false);
        assertCode(() -> service.open(orga, p.id()), "POT_INCOMPLETE");
        assertThatThrownBy(() -> service.goal(orga, p.id(), new GoalStep(null, Instant.now().plus(61, ChronoUnit.DAYS)))).isInstanceOf(ValidationException.class);
        service.goal(orga, p.id(), new GoalStep(null, Instant.now().plus(1, ChronoUnit.DAYS)));
        assertThatThrownBy(() -> service.invitees(orga, p.id(), new InviteesStep(List.of(lea.id())))).isInstanceOf(ValidationException.class);
        service.invitees(orga, p.id(), new InviteesStep(List.of(alice.id())));
        assertThat(service.review(orga, p.id()).canOpen()).isTrue();
        assertThat(service.open(orga, p.id()).status()).isEqualTo(PotStatus.OPEN);
        assertCode(() -> service.goal(orga, p.id(), new GoalStep(null, Instant.now().plus(2, ChronoUnit.DAYS))), "INVALID_STATUS_TRANSITION");
    }

    @Test
    void beneficiaryAndOutsidersCannotSeeTheOpenPot() {
        PotDto p = openPot();
        assertCode(() -> service.get(lea, p.id()), "NOT_FOUND");
        assertCode(() -> service.card(lea, p.id()), "NOT_FOUND");
        assertCode(() -> service.get(outsider, p.id()), "NOT_FOUND");
        assertCode(() -> service.contribute(lea, p.id(), new ContributionRequest(new BigDecimal("5"), null)), "NOT_FOUND");
        assertThat(service.mine(lea)).isEmpty();
        assertThat(service.mine(alice)).hasSize(1);
    }

    @Test
    void amountsAreVisibleToTheOrganizerOnly() {
        PotDto p = openPot();
        service.contribute(alice, p.id(), new ContributionRequest(new BigDecimal("20"), "Bonne route !"));
        PotDto asBob = service.contribute(bob, p.id(), new ContributionRequest(new BigDecimal("10"), null));
        assertThat(asBob.total()).isEqualByComparingTo("30.00");
        assertThat(asBob.progress()).isEqualTo(60);
        assertThat(asBob.participants()).allMatch(x -> x.amount() == null);
        assertThat(service.get(orga, p.id()).participants()).extracting(ParticipantDto::amount).containsExactly(new BigDecimal("20.00"), new BigDecimal("10.00"));
        PotDto updated = service.contribute(alice, p.id(), new ContributionRequest(new BigDecimal("25"), "Bonne route !"));
        assertThat(updated.total()).isEqualByComparingTo("35.00");
        assertThat(updated.participants()).hasSize(2);
        assertCode(() -> service.delete(orga, p.id()), "POT_HAS_CONTRIBUTIONS");
    }

    @Test
    void closingRevealsTheCardToTheBeneficiaryAndFreezesContributions() {
        PotDto p = openPot();
        service.contribute(alice, p.id(), new ContributionRequest(new BigDecimal("20"), "Bonne route !"));
        assertCode(() -> service.close(alice, p.id()), "FORBIDDEN");
        service.close(orga, p.id());
        CardDto card = service.card(lea, p.id());
        assertThat(card.messages()).extracting(CardMessage::message).containsExactly("Bonne route !");
        assertThat(service.get(lea, p.id()).role()).isEqualTo("BENEFICIARY");
        assertCode(() -> service.contribute(bob, p.id(), new ContributionRequest(new BigDecimal("5"), null)), "POT_CLOSED");
    }

    @Test
    void potClosesAutomaticallyAtClosesAt() {
        PotDto p = openPot();
        jdbc.update("update farewell_pots set closes_at = now() - interval '1 minute' where id = ?", p.id());
        assertThat(service.get(lea, p.id()).status()).isEqualTo(PotStatus.CLOSED);
        assertCode(() -> service.contribute(alice, p.id(), new ContributionRequest(new BigDecimal("5"), null)), "POT_CLOSED");
    }
}
