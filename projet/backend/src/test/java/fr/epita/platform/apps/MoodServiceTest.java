package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.mood.dto.MoodDtos.MoodEntryRequest;
import fr.epita.platform.apps.mood.service.MoodService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MoodServiceTest extends IntegrationTest {

    @Autowired
    MoodService service;

    @Test
    void aSecondEntryTheSameDayUpdatesTheFirstOne() {
        AuthUser alice = auth(user("Alice"));
        LocalDate today = LocalDate.now();
        service.upsert(alice, today, new MoodEntryRequest(2, "bof", List.of("Fatigue")));
        service.upsert(alice, today, new MoodEntryRequest(4, "mieux", List.of("sport")));

        var history = service.history(alice, today.minusDays(1), today);
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().score()).isEqualTo(4);
        assertThat(history.getFirst().tags()).containsExactly("sport");
    }

    @Test
    void usersOnlySeeTheirOwnEntries() {
        AuthUser alice = auth(user("Alice"));
        AuthUser bob = auth(user("Bob"));
        LocalDate today = LocalDate.now();
        service.upsert(alice, today, new MoodEntryRequest(5, null, null));

        assertThat(service.history(bob, today.minusDays(7), today)).isEmpty();
    }

    @Test
    void futureDatesAreRefused() {
        AuthUser alice = auth(user("Alice"));
        assertThatThrownBy(() -> service.upsert(alice, LocalDate.now().plusDays(1), new MoodEntryRequest(3, null, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void statsComputeAverageAndStreak() {
        AuthUser alice = auth(user("Alice"));
        LocalDate today = LocalDate.now();
        service.upsert(alice, today, new MoodEntryRequest(4, null, null));
        service.upsert(alice, today.minusDays(1), new MoodEntryRequest(2, null, null));
        service.upsert(alice, today.minusDays(3), new MoodEntryRequest(3, null, null));

        var stats = service.stats(alice, "week");
        assertThat(stats.count()).isEqualTo(3);
        assertThat(stats.average()).isEqualTo(3.0);
        assertThat(stats.currentStreak()).isEqualTo(2);
        assertThat(stats.distribution().get(4)).isEqualTo(1);
    }

    @Test
    void adminStatsAreReservedToAdmins() throws Exception {
        mvc.perform(get("/api/mood/admin/stats").header("Authorization", bearer(user("Alice"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/mood/admin/stats").header("Authorization", bearer(admin("Admin"))))
                .andExpect(status().isOk());
    }
}
