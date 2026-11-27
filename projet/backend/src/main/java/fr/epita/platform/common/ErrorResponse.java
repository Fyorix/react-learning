package fr.epita.platform.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/** Format d'erreur unique (§4.5), déclaré une seule fois dans OpenAPI sous le nom {@code ErrorResponse}. */
@Schema(name = "ErrorResponse", description = "Format d'erreur unique renvoyé par toutes les routes de l'API")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        @Schema(description = "Date de l'erreur (ISO 8601, UTC)", example = "2026-11-25T10:00:00Z")
        Instant timestamp,
        @Schema(description = "Code HTTP", example = "409")
        int status,
        @Schema(description = """
                Code d'erreur stable, à utiliser côté front (le message peut changer). \
                Codes génériques : VALIDATION_ERROR, BAD_REQUEST, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, \
                METHOD_NOT_ALLOWED, CONFLICT, SIMULATED_ERROR. Les codes métier de chaque opération sont listés \
                dans sa réponse 409 et dans le tableau de la description générale de l'API.""",
                example = "LAST_ADMIN_REQUIRED")
        String code,
        @Schema(description = "Message lisible, en français", example = "L'application doit conserver au moins un administrateur actif")
        String message,
        @Schema(description = "Route appelée", example = "/api/users/3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10")
        String path,
        @Schema(description = "Erreurs de validation (400 VALIDATION_ERROR uniquement) : champ → message",
                example = "{\"email\": \"doit être une adresse e-mail valide\", \"displayName\": \"ne doit pas être vide\"}")
        Map<String, String> fields
) {
    public static ErrorResponse of(int status, String code, String message, String path) {
        return new ErrorResponse(Instant.now(), status, code, message, path, null);
    }
}
