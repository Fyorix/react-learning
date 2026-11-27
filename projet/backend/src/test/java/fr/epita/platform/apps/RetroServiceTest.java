package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.retro.dto.RetroDtos.*;
import fr.epita.platform.apps.retro.entity.CardColumn;
import fr.epita.platform.apps.retro.entity.RetroPhase;
import fr.epita.platform.apps.retro.service.RetroService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetroServiceTest extends IntegrationTest {

    @Autowired
    RetroService service;

    AuthUser fac;
    AuthUser alice;
    AuthUser outsider;
    TeamDto team;
    RetroDto retro;

    @BeforeEach
    void setUp() {
        fac = auth(user("Fanny Facilitatrice"));
        alice = auth(user("Alice"));
        outsider = auth(user("Oscar"));
        team = service.createTeam(fac, new TeamRequest("Squad", List.of(alice.id())));
        retro = service.create(fac, new RetroRequest(team.id(), "Rétro", "Sprint 42"));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    private void toPhase(RetroPhase target) {
        while (service.get(fac, retro.id()).phase() != target) {
            service.nextPhase(fac, retro.id());
        }
    }

    @Test
    void cardsOfOthersAreHiddenDuringWritingAndOnlyMembersSeeTheRetro() {
        service.addCard(fac, retro.id(), new CardRequest(CardColumn.WENT_WELL, "Super démo"));
        RetroDto seen = service.addCard(alice, retro.id(), new CardRequest(CardColumn.TO_IMPROVE, "Trop de réunions"));
        assertThat(seen.cards()).extracting(CardDto::text).containsExactly("Trop de réunions");
        assertThat(seen.hiddenCards()).isEqualTo(1);
        assertCode(() -> service.get(outsider, retro.id()), "NOT_FOUND");
        assertCode(() -> service.nextPhase(alice, retro.id()), "FORBIDDEN");
        toPhase(RetroPhase.GROUPING);
        assertThat(service.get(alice, retro.id()).cards()).hasSize(2);
        assertCode(() -> service.addCard(alice, retro.id(), new CardRequest(CardColumn.WENT_WELL, "trop tard")), "PHASE_LOCKED");
    }

    @Test
    void votesAreLimitedAndTotalsHiddenUntilTheEndOfVoting() {
        service.addCard(alice, retro.id(), new CardRequest(CardColumn.TO_IMPROVE, "A"));
        service.addCard(alice, retro.id(), new CardRequest(CardColumn.TO_IMPROVE, "B"));
        toPhase(RetroPhase.GROUPING);
        List<UUID> ids = service.get(fac, retro.id()).cards().stream().map(CardDto::id).toList();
        RetroDto grouped = service.group(fac, retro.id(), new GroupRequest("Réunions", List.of(ids.get(0))));
        UUID groupId = grouped.groups().get(0).id();
        toPhase(RetroPhase.VOTING);
        assertCode(() -> service.vote(alice, retro.id(), new VotesRequest(List.of(new VoteItem(ids.get(1), 2), new VoteItem(groupId, 2)))), "NO_VOTE_LEFT");
        assertCode(() -> service.vote(alice, retro.id(), new VotesRequest(List.of(new VoteItem(groupId, 2), new VoteItem(groupId, 1)))), "NO_VOTE_LEFT");
        RetroDto voted = service.vote(alice, retro.id(), new VotesRequest(List.of(new VoteItem(groupId, 2), new VoteItem(ids.get(1), 1))));
        assertThat(voted.votesLeft()).isZero();
        assertThat(voted.groups().get(0).votes()).isNull();
        service.vote(fac, retro.id(), new VotesRequest(List.of(new VoteItem(groupId, 1))));
        toPhase(RetroPhase.DISCUSSION);
        assertThat(service.get(alice, retro.id()).groups().get(0).votes()).isEqualTo(3);
    }

    @Test
    void timerStartsPausesResumesAndNeverChangesThePhase() {
        RetroDto started = service.timer(fac, retro.id(), new TimerRequest(TimerAction.START, 300));
        assertThat(Duration.between(started.serverTime(), started.timerEndsAt()).toSeconds()).isBetween(298L, 300L);
        RetroDto paused = service.timer(fac, retro.id(), new TimerRequest(TimerAction.PAUSE, null));
        assertThat(paused.timerEndsAt()).isNull();
        assertThat(paused.timerRemaining()).isBetween(298, 300);
        RetroDto added = service.timer(fac, retro.id(), new TimerRequest(TimerAction.ADD, 60));
        assertThat(added.timerRemaining()).isBetween(358, 360);
        assertThat(service.timer(fac, retro.id(), new TimerRequest(TimerAction.RESUME, null)).timerEndsAt()).isNotNull();
        assertCode(() -> service.timer(fac, retro.id(), new TimerRequest(TimerAction.RESUME, null)), "TIMER_NOT_PAUSED");
        assertThatThrownBy(() -> service.timer(fac, retro.id(), new TimerRequest(TimerAction.START, null))).isInstanceOf(ValidationException.class);
        assertThat(service.get(fac, retro.id()).phase()).isEqualTo(RetroPhase.WRITING);
    }

    @Test
    void actionsNeedATeamOwnerAndOpenActionsCarryOverToTheNextRetro() {
        toPhase(RetroPhase.ACTIONS);
        assertThatThrownBy(() -> service.addAction(alice, retro.id(), new ActionRequest("Hors équipe", outsider.id(), LocalDate.now().plusDays(7))))
                .isInstanceOf(ValidationException.class);
        RetroDto withAction = service.addAction(alice, retro.id(), new ActionRequest("Point d'équipe le mardi", alice.id(), LocalDate.now().plusDays(7)));
        toPhase(RetroPhase.CLOSED);
        assertCode(() -> service.nextPhase(fac, retro.id()), "INVALID_PHASE_TRANSITION");
        assertCode(() -> service.timer(fac, retro.id(), new TimerRequest(TimerAction.START, 60)), "PHASE_LOCKED");
        RetroDto next = service.create(fac, new RetroRequest(team.id(), "Rétro suivante", "Sprint 43"));
        assertThat(next.previousOpenActions()).extracting(ActionDto::text).containsExactly("Point d'équipe le mardi");
        UUID actionId = withAction.actions().get(0).id();
        assertCode(() -> service.setDone(outsider, actionId, true), "NOT_FOUND");
        service.setDone(alice, actionId, true);
        assertThat(service.openActions(fac, team.id())).isEmpty();
    }
}
