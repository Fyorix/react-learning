package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.association.dto.AssociationDtos.*;
import fr.epita.platform.apps.association.entity.AssociationRegistrationStatus;
import fr.epita.platform.apps.association.entity.FeeCategory;
import fr.epita.platform.apps.association.entity.VoteChoice;
import fr.epita.platform.apps.association.service.AssociationService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssociationServiceTest extends IntegrationTest {

    @Autowired
    AssociationService service;
    @Autowired
    AppRoles roles;

    AuthUser board;
    AuthUser a;
    AuthUser b;
    AuthUser c;

    @BeforeEach
    void setUp() {
        board = auth(user("Trésorière"));
        roles.grant(AssociationService.ROLES, Set.of(AssociationService.BOARD), board.id(), AssociationService.BOARD);
        a = auth(user("Adhérent A"));
        b = auth(user("Adhérent B"));
        c = auth(user("Non à jour"));
    }

    private void pay(AuthUser who) {
        service.recordFee(board, who.id(), new FeeRequest(null, FeeCategory.FULL, new BigDecimal("30"), LocalDate.now()));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void seasonsAndFees() {
        assertThat(AssociationService.seasonOf(LocalDate.of(2026, 9, 1))).isEqualTo("2026-2027");
        assertThat(AssociationService.seasonOf(LocalDate.of(2027, 8, 31))).isEqualTo("2026-2027");
        String current = AssociationService.seasonOf(LocalDate.now());
        int start = Integer.parseInt(current.substring(0, 4));
        service.recordFee(board, c.id(), new FeeRequest((start - 1) + "-" + start, FeeCategory.STUDENT, new BigDecimal("10"), LocalDate.now().minusYears(1)));
        pay(a);
        assertCode(() -> pay(a), "ALREADY_PAID");
        assertCode(() -> service.recordFee(a, b.id(), new FeeRequest(null, FeeCategory.FULL, BigDecimal.TEN, LocalDate.now())), "FORBIDDEN");
        assertThat(service.me(a).upToDate()).isTrue();
        assertThat(service.me(c).upToDate()).isFalse();
        assertThat(service.reminders(board)).extracting(MemberDto::userId).containsExactly(c.id());
        assertThat(service.members(board, true, null)).extracting(MemberDto::userId).containsExactly(a.id());
    }

    @Test
    void membersOnlyActivitiesAndWaitlist() {
        pay(a);
        pay(b);
        ActivityDto act = service.createActivity(board, new ActivityRequest("Escalade", Instant.now().plus(3, ChronoUnit.DAYS), 1, true));
        assertCode(() -> service.register(c, act.id()), "MEMBERSHIP_EXPIRED");
        assertThat(service.register(a, act.id()).myStatus()).isEqualTo(AssociationRegistrationStatus.CONFIRMED);
        ActivityDto waiting = service.register(b, act.id());
        assertThat(waiting.myStatus()).isEqualTo(AssociationRegistrationStatus.WAITLISTED);
        assertThat(waiting.myPosition()).isEqualTo(1);
        assertCode(() -> service.register(b, act.id()), "ALREADY_REGISTERED");
        service.unregister(a, act.id());
        assertThat(service.activities(b).get(0).myStatus()).isEqualTo(AssociationRegistrationStatus.CONFIRMED);
    }

    @Test
    void assemblyVoteQuorumAndResults() {
        pay(a);
        pay(b);
        pay(board);
        AssemblyDto ag = service.convene(board, new AssemblyRequest("AG 2026", Instant.now().plus(1, ChronoUnit.DAYS), 50,
                List.of(new ResolutionInput("Comptes", "Approbation des comptes"), new ResolutionInput("Cotisation", "Passage à 35 €"))));
        var r1 = ag.resolutions().get(0).id();
        var r2 = ag.resolutions().get(1).id();
        assertCode(() -> service.vote(a, ag.id(), List.of(new VoteInput(r1, VoteChoice.FOR))), "VOTE_NOT_OPEN");
        service.open(board, ag.id());
        service.vote(a, ag.id(), List.of(new VoteInput(r1, VoteChoice.FOR), new VoteInput(r2, VoteChoice.AGAINST)));
        assertCode(() -> service.vote(a, ag.id(), List.of(new VoteInput(r1, VoteChoice.AGAINST))), "ALREADY_VOTED");
        assertCode(() -> service.vote(c, ag.id(), List.of(new VoteInput(r1, VoteChoice.FOR))), "MEMBERSHIP_EXPIRED");
        service.vote(b, ag.id(), List.of(new VoteInput(r1, VoteChoice.FOR), new VoteInput(r2, VoteChoice.FOR)));
        AssemblyDto during = service.assembly(c, ag.id());
        assertThat(during.voters()).isEqualTo(2);
        assertThat(during.resolutions().get(0).forCount()).isNull();
        AssemblyDto closed = service.close(board, ag.id());
        assertThat(closed.eligible()).isEqualTo(3);
        assertThat(closed.quorumReached()).isTrue();
        assertThat(closed.resolutions().get(0).adopted()).isTrue();
        assertThat(closed.resolutions().get(1).adopted()).isFalse();
        assertThat(closed.resolutions().get(1).forCount()).isEqualTo(1);
    }

    @Test
    void noQuorumMeansNothingIsAdopted() {
        pay(a);
        pay(b);
        pay(c);
        pay(board);
        AssemblyDto ag = service.convene(board, new AssemblyRequest("AGE", Instant.now(), 50, List.of(new ResolutionInput("Statuts", "Modification"))));
        service.open(board, ag.id());
        service.vote(a, ag.id(), List.of(new VoteInput(ag.resolutions().get(0).id(), VoteChoice.FOR)));
        AssemblyDto closed = service.close(board, ag.id());
        assertThat(closed.quorumReached()).isFalse();
        assertThat(closed.resolutions().get(0).adopted()).isFalse();
    }
}
