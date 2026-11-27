package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.kudos.dto.KudosDtos.KudoDto;
import fr.epita.platform.apps.kudos.dto.KudosDtos.KudoRequest;
import fr.epita.platform.apps.kudos.dto.KudosDtos.ValueDto;
import fr.epita.platform.apps.kudos.dto.KudosDtos.WallDto;
import fr.epita.platform.apps.kudos.service.KudosService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KudosServiceTest extends IntegrationTest {

    @Autowired
    KudosService service;

    AuthUser admin;
    AuthUser alice;
    AuthUser bob;
    ValueDto entraide;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        entraide = service.addValue(admin, "Entraide");
    }

    private KudoDto send(AuthUser from, AuthUser to, String message) {
        return service.send(from, new KudoRequest(List.of(to.id()), message, entraide.id()));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void cursorPaginationIsStableWhenNewKudosArrive() {
        List<UUID> sent = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            sent.add(send(i % 2 == 0 ? alice : bob, i % 2 == 0 ? bob : alice, "Merci numéro " + i).id());
        }
        WallDto first = service.wall(alice, null, 2, null, null);
        assertThat(first.items()).extracting(KudoDto::id).containsExactly(sent.get(4), sent.get(3));
        send(admin, alice, "Un nouveau kudo arrive");
        WallDto second = service.wall(alice, first.nextCursor(), 2, null, null);
        assertThat(second.items()).extracting(KudoDto::id).containsExactly(sent.get(2), sent.get(1));
        WallDto last = service.wall(alice, second.nextCursor(), 2, null, null);
        assertThat(last.items()).extracting(KudoDto::id).containsExactly(sent.get(0));
        assertThat(last.nextCursor()).isNull();
    }

    @Test
    void noSelfKudoAndDailyLimit() {
        assertCode(() -> send(alice, alice, "Bravo à moi-même"), "SELF_KUDO");
        for (int i = 0; i < 5; i++) {
            send(alice, bob, "Merci encore " + i);
        }
        assertCode(() -> send(alice, bob, "Un de trop ici"), "DAILY_LIMIT_REACHED");
    }

    @Test
    void reactionsAreTogglesOnePerTypeAndUser() {
        KudoDto k = send(alice, bob, "Merci pour la démo");
        service.react(bob, k.id(), "🎉", true);
        service.react(bob, k.id(), "🎉", true);
        KudoDto after = service.react(admin, k.id(), "🎉", true);
        assertThat(after.reactions()).singleElement().extracting("count").isEqualTo(2L);
        assertThat(service.react(bob, k.id(), "🎉", false).reactions().get(0).mine()).isFalse();
        assertThatThrownBy(() -> service.react(bob, k.id(), "💩", true)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void authorDeletesWithinAnHourAdminModerates() {
        KudoDto k = send(alice, bob, "Merci pour tout");
        assertCode(() -> service.delete(bob, k.id()), "FORBIDDEN");
        jdbc.update("update kudos set created_at = ? where id = ?", Timestamp.from(Instant.now().minus(Duration.ofHours(2))), k.id());
        assertCode(() -> service.delete(alice, k.id()), "DELETE_WINDOW_EXPIRED");
        service.delete(admin, k.id());
        assertThat(service.wall(alice, null, 20, null, null).items()).isEmpty();
        assertThat(service.leaderboard(null)).isEmpty();
    }

    @Test
    void leaderboardAndProfile() {
        send(alice, bob, "Merci Bob une fois");
        send(admin, bob, "Merci Bob deux fois");
        send(bob, alice, "Merci Alice !!!");
        assertThat(service.leaderboard(null)).extracting(r -> r.user().displayName()).containsExactly("Bob", "Alice");
        var profile = service.profile(alice, bob.id());
        assertThat(profile.received()).isEqualTo(2);
        assertThat(profile.sent()).isEqualTo(1);
        assertThat(profile.receivedByValue()).containsEntry("Entraide", 2L);
    }
}
