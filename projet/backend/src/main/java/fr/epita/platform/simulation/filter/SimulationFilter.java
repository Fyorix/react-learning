package fr.epita.platform.simulation.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.epita.platform.common.ErrorResponse;
import fr.epita.platform.simulation.dto.SimulationDtos.LatencyLevel;
import fr.epita.platform.simulation.service.SimulationService;
import fr.epita.platform.simulation.service.Sleeper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.OptionalInt;

/**
 * Applique la simulation (§4.6) : la latence d'abord, puis la panne.
 * Placé après la chaîne de sécurité (voir SecurityConfig) : une requête sans jeton valide reçoit le vrai 401.
 * Une requête en panne n'atteint jamais le contrôleur : aucune écriture en base.
 */
@Component
@RequiredArgsConstructor
public class SimulationFilter extends OncePerRequestFilter {

    public static final String LATENCY_HEADER = "X-Simulated-Latency";
    public static final String FAILURE_HEADER = "X-Simulated-Failure";

    private final SimulationService simulation;
    private final Sleeper sleeper;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !path.startsWith("/api/")
                || path.equals("/api/simulation") || path.startsWith("/api/simulation/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        SimulationService.State state = simulation.state();

        if (state.latency() != LatencyLevel.NORMAL) {
            response.setHeader(LATENCY_HEADER, state.latency().name());
            Duration delay = SimulationService.delayOf(state.latency());
            try {
                sleeper.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        String path = request.getRequestURI().substring(request.getContextPath().length());
        OptionalInt failure = isFailureTarget(path) ? simulation.drawFailure(state) : OptionalInt.empty();
        if (failure.isPresent()) {
            writeFailure(request, response, failure.getAsInt());
            return;
        }
        chain.doFilter(request, response);
    }

    /** Pannes : uniquement les mini-applications, jamais le socle (auth, utilisateurs, simulation). */
    static boolean isFailureTarget(String path) {
        return path.startsWith("/api/")
                && !path.equals("/api/auth") && !path.startsWith("/api/auth/")
                && !path.equals("/api/users") && !path.startsWith("/api/users/")
                && !path.equals("/api/simulation") && !path.startsWith("/api/simulation/");
    }

    private void writeFailure(HttpServletRequest request, HttpServletResponse response, int status)
            throws IOException {
        response.setStatus(status);
        response.setHeader(FAILURE_HEADER, "true");
        if (status == 429 || status == 503) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "5");
        }
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.of(status, "SIMULATED_ERROR",
                "Erreur simulée (HTTP " + status + ") : la simulation de pannes est active", request.getRequestURI()));
    }
}
