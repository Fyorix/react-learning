package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.idea.dto.IdeaDtos.IdeaDto;
import fr.epita.platform.apps.idea.dto.IdeaDtos.IdeaRequest;
import fr.epita.platform.apps.idea.dto.IdeaDtos.StatusRequest;
import fr.epita.platform.apps.idea.entity.IdeaCategory;
import fr.epita.platform.apps.idea.entity.IdeaStatus;
import fr.epita.platform.apps.idea.service.IdeaService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdeaServiceTest extends IntegrationTest {

    @Autowired
    IdeaService service;

    private IdeaDto idea(AuthUser author) {
        return service.create(author, new IdeaRequest("Une machine à café", "Au 3e étage", IdeaCategory.WELLBEING));
    }

    @Test
    void cannotVoteForOwnIdeaNorTwice() {
        AuthUser alice = auth(user("Alice"));
        AuthUser bob = auth(user("Bob"));
        IdeaDto idea = idea(alice);

        assertThatThrownBy(() -> service.vote(alice, idea.id())).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("OWN_IDEA_VOTE");
        assertThat(service.vote(bob, idea.id()).votes()).isEqualTo(1);
        assertThatThrownBy(() -> service.vote(bob, idea.id())).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("ALREADY_VOTED");
        assertThat(service.unvote(bob, idea.id()).votes()).isZero();
    }

    @Test
    void statusWorkflowIsEnforcedAndReservedToAdmins() {
        AuthUser alice = auth(user("Alice"));
        AuthUser admin = auth(admin("Admin"));
        IdeaDto idea = idea(alice);

        assertThatThrownBy(() -> service.changeStatus(alice, idea.id(), new StatusRequest(IdeaStatus.UNDER_REVIEW, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("FORBIDDEN");
        assertThatThrownBy(() -> service.changeStatus(admin, idea.id(), new StatusRequest(IdeaStatus.DONE, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_STATUS_TRANSITION");

        service.changeStatus(admin, idea.id(), new StatusRequest(IdeaStatus.UNDER_REVIEW, null));
        service.changeStatus(admin, idea.id(), new StatusRequest(IdeaStatus.ACCEPTED, "Bonne idée"));
        assertThat(service.changeStatus(admin, idea.id(), new StatusRequest(IdeaStatus.DONE, null)).status())
                .isEqualTo(IdeaStatus.DONE);
    }

    @Test
    void authorCanEditOnlyWhileNew() {
        AuthUser alice = auth(user("Alice"));
        AuthUser admin = auth(admin("Admin"));
        IdeaDto idea = idea(alice);
        service.update(alice, idea.id(), new IdeaRequest("Deux machines à café", "Au 3e et 4e", IdeaCategory.WELLBEING));
        service.changeStatus(admin, idea.id(), new StatusRequest(IdeaStatus.UNDER_REVIEW, null));

        assertThatThrownBy(() -> service.update(alice, idea.id(),
                new IdeaRequest("Trois machines", "Partout", IdeaCategory.WELLBEING)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("IDEA_LOCKED");
    }

    @Test
    void listSortsByVotes() {
        AuthUser alice = auth(user("Alice"));
        AuthUser bob = auth(user("Bob"));
        IdeaDto first = idea(alice);
        IdeaDto second = service.create(alice, new IdeaRequest("Télétravail le vendredi", "Test", IdeaCategory.ORGANISATION));
        service.vote(bob, first.id());

        var page = service.list(bob, null, null, "votes", null, 0, 10);
        assertThat(page.content()).extracting(IdeaDto::id).containsExactly(first.id(), second.id());
        assertThat(page.content().getFirst().hasVoted()).isTrue();
    }
}
