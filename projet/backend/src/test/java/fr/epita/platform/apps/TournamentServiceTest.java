package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.tournament.dto.TournamentDtos.*;
import fr.epita.platform.apps.tournament.entity.Format;
import fr.epita.platform.apps.tournament.entity.MatchStatus;
import fr.epita.platform.apps.tournament.entity.Mode;
import fr.epita.platform.apps.tournament.entity.TournamentStatus;
import fr.epita.platform.apps.tournament.service.TournamentService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TournamentServiceTest extends IntegrationTest {

    @Autowired
    TournamentService service;
    @Autowired
    JdbcTemplate jdbc;

    List<AuthUser> players;

    @BeforeEach
    void setUp() {
        players = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            players.add(auth(user("Joueur " + i)));
        }
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    private TournamentDto create(Format format, Mode mode) {
        return service.create(players.get(0), new TournamentRequest("Coupe", format, mode, Instant.now().plus(1, ChronoUnit.DAYS)));
    }

    private AuthUser playerOf(EntryDto e) {
        return players.stream().filter(p -> p.id().equals(e.playerIds().get(0))).findFirst().orElseThrow();
    }

    private MatchDto play(MatchDto m, boolean aWins) {
        service.report(playerOf(m.entryA()), m.id(), aWins ? new ScoreRequest(10, 6) : new ScoreRequest(4, 10));
        return service.confirm(playerOf(m.entryB()), m.id());
    }

    @Test
    void eliminationBracketSeedsByEloWithByesAndAdvancesWinners() {
        // Elo connus : joueur 5 meilleur, puis 4
        jdbc.update("insert into tournament_ratings(user_id, elo, played, wins, updated_at) values (?, 1200, 5, 5, now()), (?, 1100, 5, 3, now())",
                players.get(4).id(), players.get(3).id());
        TournamentDto t = create(Format.SINGLE_ELIMINATION, Mode.SINGLES);
        for (int i = 0; i < 5; i++) {
            service.register(players.get(i), t.id(), null);
        }
        assertCode(() -> service.start(players.get(1), t.id()), "FORBIDDEN");
        TournamentDto started = service.start(players.get(0), t.id());
        assertThat(started.rounds()).isEqualTo(3);
        assertThat(started.entries().get(0).playerIds()).containsExactly(players.get(4).id());
        List<MatchDto> first = started.matches().stream().filter(m -> m.round() == 1).toList();
        assertThat(first).hasSize(4);
        assertThat(first.stream().filter(m -> m.status() == MatchStatus.BYE)).hasSize(3);
        // la tête de série 1 est exemptée et attend au tour 2
        MatchDto seed1Bye = first.get(0);
        assertThat(seed1Bye.status()).isEqualTo(MatchStatus.BYE);
        assertThat(seed1Bye.winnerId()).isEqualTo(started.entries().get(0).id());
        MatchDto real = first.stream().filter(m -> m.status() == MatchStatus.SCHEDULED).findFirst().orElseThrow();
        play(real, true);
        TournamentDto after = service.get(players.get(0), t.id());
        assertThat(after.matches().stream().filter(m -> m.round() == 2 && m.status() == MatchStatus.SCHEDULED)).hasSize(2);
        assertCode(() -> service.register(players.get(7), t.id(), null), "REGISTRATION_CLOSED");
    }

    @Test
    void scoreMustBeConfirmedByTheOpponentAndDisputeResets() {
        TournamentDto t = create(Format.ROUND_ROBIN, Mode.SINGLES);
        for (int i = 0; i < 4; i++) {
            service.register(players.get(i), t.id(), null);
        }
        MatchDto m = service.start(players.get(0), t.id()).matches().get(0);
        AuthUser a = playerOf(m.entryA());
        AuthUser b = playerOf(m.entryB());
        assertThatThrownBy(() -> service.report(a, m.id(), new ScoreRequest(10, 10))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.report(a, m.id(), new ScoreRequest(9, 8))).isInstanceOf(RuntimeException.class);
        service.report(a, m.id(), new ScoreRequest(10, 3));
        assertCode(() -> service.confirm(a, m.id()), "SCORE_NOT_CONFIRMABLE");
        AuthUser outsider = players.stream().filter(p -> !p.id().equals(a.id()) && !p.id().equals(b.id())).findFirst().orElseThrow();
        assertCode(() -> service.confirm(outsider, m.id()), "FORBIDDEN");
        assertThat(service.dispute(b, m.id()).status()).isEqualTo(MatchStatus.SCHEDULED);
        assertThat(service.ranking()).isEmpty();
        service.report(a, m.id(), new ScoreRequest(10, 3));
        service.confirm(b, m.id());
        List<RankingRow> ranking = service.ranking();
        assertThat(ranking).extracting(RankingRow::elo).containsExactly(1016, 984);
        assertThat(service.player(a, a.id()).history()).hasSize(1);
    }

    @Test
    void roundRobinFinishesWhenAllMatchesAreConfirmed() {
        TournamentDto t = create(Format.ROUND_ROBIN, Mode.SINGLES);
        for (int i = 0; i < 4; i++) {
            service.register(players.get(i), t.id(), null);
        }
        TournamentDto started = service.start(players.get(0), t.id());
        assertThat(started.matches()).hasSize(6);
        for (MatchDto m : started.matches()) {
            play(m, true);
        }
        TournamentDto done = service.get(players.get(0), t.id());
        assertThat(done.status()).isEqualTo(TournamentStatus.FINISHED);
        assertThat(done.standings()).hasSize(4);
        assertThat(done.champion()).isNotNull();
        assertThat(done.standings().get(0).wins()).isGreaterThanOrEqualTo(done.standings().get(3).wins());
    }

    @Test
    void doublesRegistrationRulesAndMinimumParticipants() {
        TournamentDto t = create(Format.SINGLE_ELIMINATION, Mode.DOUBLES);
        assertThatThrownBy(() -> service.register(players.get(0), t.id(), null)).isInstanceOf(RuntimeException.class);
        service.register(players.get(0), t.id(), players.get(1).id());
        assertCode(() -> service.register(players.get(2), t.id(), players.get(1).id()), "PARTNER_ALREADY_REGISTERED");
        assertCode(() -> service.register(players.get(1), t.id(), players.get(2).id()), "ALREADY_REGISTERED");
        service.register(players.get(2), t.id(), players.get(3).id());
        service.register(players.get(4), t.id(), players.get(5).id());
        assertCode(() -> service.start(players.get(0), t.id()), "NOT_ENOUGH_PLAYERS");
        service.register(players.get(6), t.id(), players.get(7).id());
        TournamentDto started = service.start(players.get(0), t.id());
        MatchDto m = started.matches().get(0);
        UUID winner = play(m, true).winnerId();
        assertThat(winner).isEqualTo(m.entryA().id());
        // partenaire du gagnant : Elo mis à jour aussi
        assertThat(service.player(players.get(0), m.entryA().playerIds().get(1)).elo()).isEqualTo(1016);
        assertCode(() -> service.unregister(players.get(0), t.id()), "REGISTRATION_CLOSED");
    }
}
