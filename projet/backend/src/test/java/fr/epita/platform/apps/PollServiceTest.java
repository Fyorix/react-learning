package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.poll.dto.PollDtos.PollDto;
import fr.epita.platform.apps.poll.dto.PollDtos.PollRequest;
import fr.epita.platform.apps.poll.service.PollService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PollServiceTest extends IntegrationTest {

    @Autowired
    PollService service;

    private PollDto poll(AuthUser creator, boolean multiple, boolean visibleBefore) {
        return service.create(creator, new PollRequest("Où déjeuner ?", List.of("Pizza", "Sushi", "Burger"),
                multiple, visibleBefore, null));
    }

    @Test
    void atLeastTwoOptionsAreRequired() throws Exception {
        mvc.perform(post("/api/polls").header("Authorization", bearer(user("Alice")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("question", "Seule option ?", "options", List.of("Oui")))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.options").exists());
    }

    @Test
    void oneVotePerUserWhichCanBeChanged() {
        AuthUser alice = auth(user("Alice"));
        AuthUser bob = auth(user("Bob"));
        PollDto p = poll(alice, false, true);

        service.vote(bob, p.id(), List.of(p.options().get(0).id()));
        service.vote(bob, p.id(), List.of(p.options().get(1).id()));

        var results = service.results(bob, p.id());
        assertThat(results.totalVoters()).isEqualTo(1);
        assertThat(results.options().get(0).votes()).isZero();
        assertThat(results.options().get(1).votes()).isEqualTo(1);
        assertThat(results.options().get(1).percentage()).isEqualTo(100.0);
    }

    @Test
    void singleChoicePollRejectsSeveralOptions() {
        AuthUser alice = auth(user("Alice"));
        PollDto p = poll(alice, false, true);
        assertThatThrownBy(() -> service.vote(alice, p.id(), List.of(p.options().get(0).id(), p.options().get(1).id())))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("INVALID_VOTE");
    }

    @Test
    void noVoteAfterClosing() {
        AuthUser alice = auth(user("Alice"));
        AuthUser bob = auth(user("Bob"));
        PollDto p = poll(alice, true, true);
        service.close(alice, p.id());
        assertThatThrownBy(() -> service.vote(bob, p.id(), List.of(p.options().get(0).id())))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("POLL_CLOSED");
    }

    @Test
    void onlyCreatorOrAdminCanCloseOrDelete() {
        AuthUser alice = auth(user("Alice"));
        AuthUser bob = auth(user("Bob"));
        AuthUser admin = auth(admin("Admin"));
        PollDto p = poll(alice, false, true);

        assertThatThrownBy(() -> service.close(bob, p.id())).isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("FORBIDDEN");
        assertThatThrownBy(() -> service.delete(bob, p.id())).isInstanceOf(ApiException.class);
        assertThat(service.close(admin, p.id()).open()).isFalse();
    }

    @Test
    void pollWithVotesCannotBeEdited() {
        AuthUser alice = auth(user("Alice"));
        AuthUser bob = auth(user("Bob"));
        PollDto p = poll(alice, false, true);
        service.vote(bob, p.id(), List.of(p.options().get(0).id()));
        assertThatThrownBy(() -> service.update(alice, p.id(),
                new PollRequest("Nouvelle question", List.of("A", "B"), false, true, null)))
                .isInstanceOf(ApiException.class).extracting("code").isEqualTo("POLL_HAS_VOTES");
    }

    @Test
    void hiddenResultsUntilVote() {
        AuthUser alice = auth(user("Alice"));
        AuthUser bob = auth(user("Bob"));
        PollDto p = poll(alice, false, false);
        assertThatThrownBy(() -> service.results(bob, p.id())).isInstanceOf(ApiException.class);
        service.vote(bob, p.id(), List.of(p.options().get(2).id()));
        assertThat(service.results(bob, p.id()).totalVoters()).isEqualTo(1);
    }
}
