package fr.epita.platform.simulation.service;

import fr.epita.platform.common.ValidationException;
import fr.epita.platform.simulation.dto.SimulationDtos.FailureDto;
import fr.epita.platform.simulation.dto.SimulationDtos.FailureMode;
import fr.epita.platform.simulation.dto.SimulationDtos.FailureRequest;
import fr.epita.platform.simulation.dto.SimulationDtos.LatencyLevel;
import fr.epita.platform.simulation.dto.SimulationDtos.SimulationDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

/**
 * Détient le réglage de simulation (§4.6) : en mémoire, global à l'instance, jamais persisté.
 * Au démarrage l'API repart toujours en NORMAL + NONE.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    public static final List<Integer> ALLOWED_STATUSES = List.of(400, 401, 403, 404, 409, 422, 429, 500, 502, 503);
    public static final int DEFAULT_RATE = 100;

    public record State(LatencyLevel latency, FailureMode mode, Integer status, int rate) {
        static final State DEFAULT = new State(LatencyLevel.NORMAL, FailureMode.NONE, null, DEFAULT_RATE);
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.DEFAULT);
    private final RandomGenerator random;

    public State state() {
        return state.get();
    }

    public SimulationDto current() {
        State s = state.get();
        return new SimulationDto(s.latency(), new FailureDto(s.mode(), s.status(), s.rate()));
    }

    public SimulationDto setLatency(LatencyLevel level, String changedBy) {
        state.updateAndGet(s -> new State(level, s.mode(), s.status(), s.rate()));
        log.warn("Simulation : latence réglée sur {} par {}", level, changedBy);
        return current();
    }

    public SimulationDto setFailure(FailureRequest req, String changedBy) {
        var errors = new ValidationException.Collector();
        if (req.mode() == FailureMode.FIXED) {
            errors.check(req.status() != null, "status", "est obligatoire en mode FIXED");
        } else {
            errors.check(req.status() == null, "status", "est interdit hors du mode FIXED");
        }
        errors.check(req.status() == null || ALLOWED_STATUSES.contains(req.status()), "status",
                "doit être l'un des codes " + ALLOWED_STATUSES);
        errors.throwIfAny();

        int rate = req.rate() == null ? DEFAULT_RATE : req.rate();
        state.updateAndGet(s -> new State(s.latency(), req.mode(), req.status(), rate));
        log.warn("Simulation : pannes réglées sur {} (status={}, rate={}%) par {}",
                req.mode(), req.status(), rate, changedBy);
        return current();
    }

    public SimulationDto reset(String changedBy) {
        state.set(State.DEFAULT);
        log.warn("Simulation : remise à zéro (NORMAL + NONE) par {}", changedBy);
        return current();
    }

    public static Duration delayOf(LatencyLevel level) {
        return switch (level) {
            case NORMAL -> Duration.ZERO;
            case LOW -> Duration.ofSeconds(1);
            case MEDIUM -> Duration.ofSeconds(10);
            case HIGH -> Duration.ofSeconds(60);
        };
    }

    /** Code d'erreur à forcer pour une requête ciblée, ou vide si elle doit être traitée normalement. */
    public OptionalInt drawFailure(State s) {
        if (s.mode() == FailureMode.NONE) {
            return OptionalInt.empty();
        }
        if (s.rate() < 100 && random.nextInt(100) >= s.rate()) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(s.mode() == FailureMode.FIXED
                ? s.status()
                : ALLOWED_STATUSES.get(random.nextInt(ALLOWED_STATUSES.size())));
    }
}
