package fr.epita.platform.simulation.controller;

import fr.epita.platform.common.AuthUser;
import fr.epita.platform.simulation.dto.SimulationDtos.FailureRequest;
import fr.epita.platform.simulation.dto.SimulationDtos.LatencyRequest;
import fr.epita.platform.simulation.dto.SimulationDtos.SimulationDto;
import fr.epita.platform.simulation.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Simulation", description = "Latence et pannes simulées pour tester les états de chargement et d'erreur des fronts.")
public class SimulationController {

    private final SimulationService service;

    @GetMapping
    @Operation(summary = "Réglage courant de la simulation",
            description = "Accès : ADMIN. L'état est en mémoire et repart en NORMAL + NONE à chaque démarrage.")
    @ApiResponse(responseCode = "200", description = "Réglage courant")
    @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN")
    public SimulationDto get() {
        return service.current();
    }

    @PutMapping("/latency")
    @Operation(summary = "Régler la latence",
            description = """
                    Accès : ADMIN. Ajoute un délai avant chaque requête `/api/**` (y compris auth et users), \
                    sauf `/api/simulation/**`, les requêtes OPTIONS et Swagger. \
                    Les réponses concernées portent l'en-tête `X-Simulated-Latency`. \
                    Attention : en HIGH (60 s), certains proxys coupent la connexion.""")
    @ApiResponse(responseCode = "200", description = "Nouveau réglage")
    @ApiResponse(responseCode = "400", description = "Niveau absent ou inconnu (VALIDATION_ERROR, BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN")
    public SimulationDto latency(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody LatencyRequest req) {
        return service.setLatency(req.level(), me.email());
    }

    @PutMapping("/failure")
    @Operation(summary = "Régler les pannes",
            description = """
                    Accès : ADMIN. Ne touche que les routes des mini-applications (`/api/<application>/**`), \
                    jamais `/api/auth/**`, `/api/users/**` ni `/api/simulation/**`. \
                    Une requête en panne n'est pas traitée (aucune écriture en base) et renvoie le format d'erreur \
                    unique avec `code: SIMULATED_ERROR`, l'en-tête `X-Simulated-Failure: true` \
                    et `Retry-After: 5` en 429 et 503. \
                    Règles : `status` obligatoire en FIXED et interdit sinon ; `rate` entre 1 et 100 (100 par défaut).""")
    @ApiResponse(responseCode = "200", description = "Nouveau réglage")
    @ApiResponse(responseCode = "400", description = "Mode absent, `status` manquant, interdit ou non autorisé, `rate` hors bornes (VALIDATION_ERROR)")
    @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN")
    public SimulationDto failure(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody FailureRequest req) {
        return service.setFailure(req, me.email());
    }

    @DeleteMapping
    @Operation(summary = "Remettre la simulation à zéro", description = "Accès : ADMIN. Revient à NORMAL + NONE.")
    @ApiResponse(responseCode = "200", description = "Réglage par défaut")
    @ApiResponse(responseCode = "401", description = "Jeton absent ou invalide")
    @ApiResponse(responseCode = "403", description = "Réservé aux ADMIN")
    public SimulationDto reset(@AuthenticationPrincipal AuthUser me) {
        return service.reset(me.email());
    }
}
