package fr.epita.platform.simulation;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.simulation.dto.SimulationDtos.FailureMode;
import fr.epita.platform.simulation.dto.SimulationDtos.FailureRequest;
import fr.epita.platform.simulation.dto.SimulationDtos.LatencyLevel;
import fr.epita.platform.simulation.service.SimulationService;
import fr.epita.platform.simulation.service.Sleeper;
import fr.epita.platform.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** §4.6 : latence et pannes simulées. Le {@link Sleeper} est remplacé : aucun test n'attend réellement. */
class SimulationApiTest extends IntegrationTest {

    @MockBean
    Sleeper sleeper;

    User admin;
    User alice;

    @BeforeEach
    void setUp() {
        admin = admin("Admin");
        alice = user("Alice");
    }

    static List<Integer> allowedStatuses() {
        return SimulationService.ALLOWED_STATUSES;
    }

    private void setFailure(String mode, Integer status, Integer rate) throws Exception {
        var body = new java.util.HashMap<String, Object>();
        body.put("mode", mode);
        if (status != null) {
            body.put("status", status);
        }
        if (rate != null) {
            body.put("rate", rate);
        }
        mvc.perform(put("/api/simulation/failure").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                .andExpect(status().isOk());
    }

    private void setLatency(String level) throws Exception {
        mvc.perform(put("/api/simulation/latency").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("level", level))))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @MethodSource("allowedStatuses")
    void fixedModeReturnsTheChosenStatusOnMiniApps(int code) throws Exception {
        setFailure("FIXED", code, null);
        var result = mvc.perform(get("/api/polls").header("Authorization", bearer(alice)))
                .andExpect(status().is(code))
                .andExpect(header().string("X-Simulated-Failure", "true"))
                .andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.code").value("SIMULATED_ERROR"))
                .andExpect(jsonPath("$.path").value("/api/polls"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn();
        if (code == 429 || code == 503) {
            assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("5");
        } else {
            assertThat(result.getResponse().getHeader("Retry-After")).isNull();
        }
    }

    @Test
    void randomModeOnlyReturnsAllowedStatuses() throws Exception {
        setFailure("RANDOM", null, null);
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 60; i++) {
            MvcResult r = mvc.perform(get("/api/polls").header("Authorization", bearer(alice))).andReturn();
            seen.add(r.getResponse().getStatus());
            assertThat(r.getResponse().getHeader("X-Simulated-Failure")).isEqualTo("true");
        }
        assertThat(SimulationService.ALLOWED_STATUSES).containsAll(seen);
        assertThat(seen.size()).as("un mélange de codes").isGreaterThan(3);
    }

    @Test
    void rateIsRespectedOnASample() {
        simulationService.setFailure(new FailureRequest(FailureMode.RANDOM, null, 30), "test");
        SimulationService.State state = simulationService.state();
        int failures = 0;
        int sample = 5000;
        for (int i = 0; i < sample; i++) {
            if (simulationService.drawFailure(state).isPresent()) {
                failures++;
            }
        }
        assertThat(failures / (double) sample).isBetween(0.25, 0.35);
    }

    @Test
    void rateIsAppliedOverHttp() throws Exception {
        setFailure("FIXED", 500, 50);
        int failures = 0;
        for (int i = 0; i < 100; i++) {
            if (mvc.perform(get("/api/polls").header("Authorization", bearer(alice))).andReturn()
                    .getResponse().getStatus() == 500) {
                failures++;
            }
        }
        assertThat(failures).isBetween(25, 75);
    }

    @Test
    void authUsersAndSimulationRoutesAreNeverOverridden() throws Exception {
        setFailure("FIXED", 503, null);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Simulated-Failure"));
        mvc.perform(get("/api/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/simulation").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failure.mode").value("FIXED"));
    }

    @Test
    void noDatabaseWriteDuringASimulatedFailure() throws Exception {
        setFailure("FIXED", 500, null);
        mvc.perform(put("/api/mood/entries/2026-11-20").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("score", 4))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SIMULATED_ERROR"));
        assertThat(jdbc.queryForObject("select count(*) from mood_entries", Long.class)).isZero();
    }

    @Test
    void securityComesFirst() throws Exception {
        setFailure("FIXED", 500, null);
        mvc.perform(get("/api/polls"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void deleteResetsTheDefaultState() throws Exception {
        setLatency("MEDIUM");
        setFailure("RANDOM", null, 40);
        mvc.perform(delete("/api/simulation").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latency").value("NORMAL"))
                .andExpect(jsonPath("$.failure.mode").value("NONE"))
                .andExpect(jsonPath("$.failure.rate").value(100));
        mvc.perform(get("/api/polls").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Simulated-Latency"));
    }

    @Test
    void simulationIsReservedToAdmins() throws Exception {
        mvc.perform(get("/api/simulation").header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/simulation/latency").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("level", "HIGH"))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/simulation").header("Authorization", bearer(alice)))
                .andExpect(status().isForbidden());
        assertThat(simulationService.state().latency()).isEqualTo(LatencyLevel.NORMAL);
    }

    @Test
    void invalidFailureSettingsAreRejected() throws Exception {
        var cases = List.of(
                Map.of("mode", "FIXED"),                         // status manquant
                Map.of("mode", "RANDOM", "status", 500),          // status interdit
                Map.of("mode", "FIXED", "status", 418),           // code non autorisé
                Map.of("mode", "RANDOM", "rate", 0));             // rate hors bornes
        for (var body : cases) {
            mvc.perform(put("/api/simulation/failure").header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(toJson(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(simulationService.state().mode()).isEqualTo(FailureMode.NONE);
    }

    @Test
    void latencyDelaysEveryApiRouteExceptSimulation() throws Exception {
        setLatency("HIGH");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(alice)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Simulated-Latency", "HIGH"));
        verify(sleeper).sleep(Duration.ofSeconds(60));

        org.mockito.Mockito.clearInvocations(sleeper);
        mvc.perform(get("/api/simulation").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Simulated-Latency"));
        mvc.perform(options("/api/polls").header("Origin", "http://localhost:5102")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().doesNotExist("X-Simulated-Latency"));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        verify(sleeper, never()).sleep(any());
    }

    @Test
    void latencyLevelsMapToTheirDelays() {
        assertThat(SimulationService.delayOf(LatencyLevel.NORMAL)).isZero();
        assertThat(SimulationService.delayOf(LatencyLevel.LOW)).isEqualTo(Duration.ofSeconds(1));
        assertThat(SimulationService.delayOf(LatencyLevel.MEDIUM)).isEqualTo(Duration.ofSeconds(10));
        assertThat(SimulationService.delayOf(LatencyLevel.HIGH)).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void latencyAndFailureAreCumulative() throws Exception {
        setLatency("LOW");
        setFailure("FIXED", 502, null);
        mvc.perform(get("/api/polls").header("Authorization", bearer(alice)))
                .andExpect(status().isBadGateway())
                .andExpect(header().string("X-Simulated-Latency", "LOW"))
                .andExpect(header().string("X-Simulated-Failure", "true"));
        verify(sleeper).sleep(Duration.ofSeconds(1));
    }
}
