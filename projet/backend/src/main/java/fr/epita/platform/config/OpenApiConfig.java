package fr.epita.platform.config;

import fr.epita.platform.common.ErrorResponse;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Documentation OpenAPI (§4.7). Les contrôleurs déclarent chaque code de réponse avec {@code @ApiResponse} ;
 * ce fichier déclare une seule fois le schéma {@code ErrorResponse} et le branche sur toutes les réponses d'erreur.
 */
@Configuration
public class OpenApiConfig {

    public static final String BEARER = "bearerAuth";
    private static final String ERROR_REF = "#/components/schemas/ErrorResponse";
    private static final Pattern CODE = Pattern.compile("\\b[A-Z][A-Z_]{3,}\\b");

    private static final String DESCRIPTION = """
            Authentification, utilisateurs et API des 11 mini-applications.

            **Authentification** : appeler `POST /api/auth/login`, puis cliquer sur *Authorize* \
            et coller le `accessToken` (sans le préfixe `Bearer`).

            **Erreurs** : toutes les réponses d'erreur suivent le schéma `ErrorResponse`. \
            Utiliser le champ `code`, stable, plutôt que le `message`.

            | Codes | Statut | Application |
            |---|---|---|
            | VALIDATION_ERROR (détail dans `fields`), BAD_REQUEST | 400 | toutes |
            | UNAUTHORIZED, BAD_CREDENTIALS, INVALID_TOKEN | 401 | toutes / Auth |
            | FORBIDDEN, ACCOUNT_DISABLED | 403 | toutes / Auth |
            | NOT_FOUND, MENU_NOT_PUBLISHED | 404 | toutes / Cantine |
            | INVALID_PASSWORD | 400 | Auth |
            | LAST_ADMIN_REQUIRED, EMAIL_ALREADY_USED, USER_HOLDS_EQUIPMENT | 409 | Users |
            | POLL_CLOSED, POLL_HAS_VOTES | 409 | Sondages |
            | INVALID_VOTE | 400 | Sondages |
            | ALREADY_VOTED, OWN_IDEA_VOTE, IDEA_LOCKED, INVALID_STATUS_TRANSITION | 409 | Boîte à idées |
            | LOAN_LIMIT_REACHED, BOOK_UNAVAILABLE, EXTENSION_NOT_ALLOWED, OVERDUE_LOANS, ALREADY_BORROWED, ALREADY_IN_QUEUE, BOOK_AVAILABLE, BOOK_HAS_ACTIVE_LOAN, LOAN_ALREADY_RETURNED | 409 | Bibliothèque |
            | NO_SEAT_AVAILABLE, TRIP_NOT_BOOKABLE, ALREADY_BOOKED, OWN_TRIP, SEATS_BELOW_BOOKINGS, TRIP_CANCELLED | 409 | Covoiturage |
            | QUIZ_PUBLISHED, QUIZ_HAS_ATTEMPTS, ALREADY_ATTEMPTED | 409 | Quiz |
            | INVALID_ATTEMPT | 400 | Quiz |
            | TOO_MANY_GOALS, GOAL_CLOSED, GOAL_LOCKED, INVALID_STATUS_TRANSITION, SELF_MANAGER, REPORTING_CYCLE | 409 | Objectifs |
            | NOT_ENOUGH_PARTICIPANTS, DRAW_IMPOSSIBLE, DRAW_ALREADY_DONE, DRAW_NOT_DONE, EVENT_PASSED | 409 | Secret Santa |
            | REGISTRATION_CLOSED, ALREADY_REGISTERED, ORGANIZER_ALREADY_IN, CAPACITY_BELOW_CONFIRMED, EVENT_CANCELLED | 409 | Événements |
            | CATALOG_INCOMPLETE, DISH_IN_USE, DISH_ALREADY_EXISTS, MENU_INCOMPLETE, MENU_PUBLISHED, VEGETARIAN_DAYS_UNREACHABLE, NOT_ENOUGH_VEGETARIAN_DISHES | 409 | Cantine |
            | EQUIPMENT_NOT_AVAILABLE, EQUIPMENT_NOT_ASSIGNED, EQUIPMENT_SCRAPPED, SERIAL_NUMBER_ALREADY_USED, ISSUE_ALREADY_OPEN, USER_DISABLED | 409 | Inventaire |
            | CONFLICT | 409 | toutes (contrainte d'unicité en base) |
            | SIMULATED_ERROR | 400 à 503 | mini-applications, quand la simulation de pannes est active |

            **Simulation** : un ADMIN peut ajouter de la latence et des pannes (tag *Simulation*). \
            Les réponses concernées portent les en-têtes `X-Simulated-Latency` et `X-Simulated-Failure`.
            """;

    @Bean
    public OpenAPI platformOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Plateforme EPITA MTI — API").version("1.0.0").description(DESCRIPTION))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                                .description("Jeton d'accès renvoyé par POST /api/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** Déclare {@code ErrorResponse} une fois et le référence depuis chaque réponse 4xx / 5xx. */
    @Bean
    public OpenApiCustomizer errorResponsesCustomizer() {
        return openApi -> {
            Map<String, Schema> schemas = ModelConverters.getInstance()
                    .readAll(new AnnotatedType(ErrorResponse.class));
            schemas.forEach(openApi.getComponents()::addSchemas);

            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().values().forEach(path -> path.readOperations().forEach(op -> {
                if (op.getResponses() == null) {
                    return;
                }
                op.getResponses().forEach((code, response) -> {
                    if (code.startsWith("4") || code.startsWith("5")) {
                        response.setContent(errorContent(code, response.getDescription()));
                    }
                });
            }));
        };
    }

    private static Content errorContent(String code, String description) {
        MediaType media = new MediaType().schema(new Schema<>().$ref(ERROR_REF));
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("timestamp", "2026-11-25T10:00:00Z");
        example.put("status", Integer.parseInt(code));
        switch (code) {
            case "400" -> {
                example.put("code", "VALIDATION_ERROR");
                example.put("message", "Données invalides");
                example.put("path", "/api/users");
                example.put("fields", Map.of(
                        "email", "doit être une adresse e-mail valide",
                        "displayName", "ne doit pas être vide"));
            }
            case "401" -> {
                example.put("code", "UNAUTHORIZED");
                example.put("message", "Authentification requise");
                example.put("path", "/api/auth/me");
            }
            case "403" -> {
                example.put("code", "FORBIDDEN");
                example.put("message", "Accès refusé");
                example.put("path", "/api/users");
            }
            case "404" -> {
                example.put("code", "NOT_FOUND");
                example.put("message", "Ressource introuvable");
                example.put("path", "/api/polls/3f6c1f0e-8a7b-4c39-9d43-2f0d5c8e1a10");
            }
            case "409" -> {
                // le premier code métier cité dans la description de la réponse sert d'exemple
                Matcher m = CODE.matcher(description == null ? "" : description);
                example.put("code", m.find() ? m.group() : "CONFLICT");
                example.put("message", "Conflit avec une règle métier");
                example.put("path", "/api/...");
            }
            default -> {
                example.put("code", "SIMULATED_ERROR");
                example.put("message", "Erreur simulée (HTTP " + code + ") : la simulation de pannes est active");
                example.put("path", "/api/polls");
            }
        }
        media.addExamples("exemple", new Example().value(example));
        return new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, media);
    }
}
