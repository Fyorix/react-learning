package fr.epita.platform.simulation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class SimulationDtos {

    private SimulationDtos() {
    }

    @Schema(description = """
            Niveau de latence ajouté avant le traitement de chaque requête :
            NORMAL = aucun délai, LOW = 1 s, MEDIUM = 10 s, HIGH = 60 s.""")
    public enum LatencyLevel {
        NORMAL, LOW, MEDIUM, HIGH
    }

    @Schema(description = """
            Mode de panne : NONE = réponses normales, FIXED = toutes les requêtes ciblées renvoient `status`,
            RANDOM = chaque requête ciblée renvoie un code tiré au hasard parmi les codes autorisés.""")
    public enum FailureMode {
        NONE, FIXED, RANDOM
    }

    @Schema(description = "Changement du niveau de latence")
    public record LatencyRequest(
            @Schema(description = "Niveau de latence (NORMAL : aucun, LOW : 1 s, MEDIUM : 10 s, HIGH : 60 s)",
                    example = "LOW")
            @NotNull LatencyLevel level) {
    }

    @Schema(description = """
            Changement du mode de panne. `status` est obligatoire en FIXED et interdit dans les autres modes.
            Codes autorisés : 400, 401, 403, 404, 409, 422, 429, 500, 502, 503.""")
    public record FailureRequest(
            @Schema(description = "Mode de panne (NONE, FIXED ou RANDOM)", example = "RANDOM")
            @NotNull FailureMode mode,
            @Schema(description = """
                    Code HTTP renvoyé en mode FIXED (obligatoire en FIXED, interdit sinon). \
                    Valeurs autorisées : 400, 401, 403, 404, 409, 422, 429, 500, 502, 503""",
                    example = "503", allowableValues = {"400", "401", "403", "404", "409", "422", "429", "500", "502", "503"},
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Integer status,
            @Schema(description = "Pourcentage (1 à 100) des requêtes ciblées qui échouent ; 100 par défaut",
                    example = "30", defaultValue = "100", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            @Min(1) @Max(100) Integer rate) {
    }

    @Schema(description = "Réglage de panne courant")
    public record FailureDto(
            @Schema(description = "Mode de panne") FailureMode mode,
            @Schema(description = "Code forcé en mode FIXED, null sinon", example = "503") Integer status,
            @Schema(description = "Pourcentage des requêtes ciblées qui échouent", example = "100") int rate) {
    }

    @Schema(description = "Réglage courant de la simulation")
    public record SimulationDto(
            @Schema(description = "Niveau de latence") LatencyLevel latency,
            FailureDto failure) {
    }
}
