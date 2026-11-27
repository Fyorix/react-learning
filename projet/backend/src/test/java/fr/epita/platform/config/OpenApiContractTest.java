package fr.epita.platform.config;

import com.fasterxml.jackson.databind.JsonNode;
import fr.epita.platform.IntegrationTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §4.7 : la documentation Swagger doit suffire pour écrire un front. Le test échoue si une opération n'a pas
 * de summary, ne documente aucune réponse d'erreur, ou si un champ de DTO de requête n'a pas de description.
 */
class OpenApiContractTest extends IntegrationTest {

    private JsonNode docs() throws Exception {
        String body = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(body);
    }

    @Test
    void everyOperationHasASummaryAndDocumentedErrors() throws Exception {
        JsonNode paths = docs().get("paths");
        List<String> problems = new ArrayList<>();
        paths.fields().forEachRemaining(path -> path.getValue().fields().forEachRemaining(op -> {
            String id = op.getKey().toUpperCase() + " " + path.getKey();
            JsonNode operation = op.getValue();
            if (operation.path("summary").asText().isBlank()) {
                problems.add(id + " : pas de summary");
            }
            boolean hasError = false;
            for (Iterator<String> it = operation.path("responses").fieldNames(); it.hasNext(); ) {
                String code = it.next();
                hasError |= code.startsWith("4") || code.startsWith("5");
            }
            if (!hasError) {
                problems.add(id + " : aucune réponse d'erreur documentée");
            }
        }));
        assertThat(problems).isEmpty();
    }

    @Test
    void everyRequestDtoFieldHasADescription() throws Exception {
        JsonNode docs = docs();
        JsonNode schemas = docs.path("components").path("schemas");
        Set<String> requestSchemas = new HashSet<>();
        docs.get("paths").forEach(path -> path.forEach(op -> {
            JsonNode schema = op.path("requestBody").path("content").path("application/json").path("schema");
            collectRefs(schema, schemas, requestSchemas);
        }));
        assertThat(requestSchemas).as("schémas de requête trouvés").hasSizeGreaterThan(30);

        List<String> problems = new ArrayList<>();
        for (String name : requestSchemas) {
            JsonNode props = schemas.path(name).path("properties");
            props.fields().forEachRemaining(p -> {
                JsonNode prop = p.getValue();
                // un champ objet est un $ref (sans description possible en OpenAPI 3.0) : c'est son schéma qui doit être décrit
                String ref = prop.path("$ref").asText();
                String described = ref.isEmpty() ? prop.path("description").asText()
                        : schemas.path(ref.substring(ref.lastIndexOf('/') + 1)).path("description").asText();
                if (described.isBlank()) {
                    problems.add(name + "." + p.getKey());
                }
            });
        }
        assertThat(problems).as("champs de requête sans description").isEmpty();
    }

    @Test
    void errorSchemaIsDeclaredOnceAndReused() throws Exception {
        JsonNode docs = docs();
        assertThat(docs.path("components").path("schemas").has("ErrorResponse")).isTrue();
        JsonNode conflict = docs.at("/paths/~1api~1users~1{id}/delete/responses/409/content/application~1json/schema/$ref");
        assertThat(conflict.asText()).isEqualTo("#/components/schemas/ErrorResponse");
        JsonNode validation = docs.at("/paths/~1api~1users/post/responses/400/content/application~1json/examples/exemple/value/fields");
        assertThat(validation.has("email")).isTrue();
    }

    @Test
    void publicRoutesAreMarkedWithoutSecurity() throws Exception {
        JsonNode docs = docs();
        assertThat(docs.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        for (String path : List.of("/api/auth/login", "/api/auth/refresh")) {
            JsonNode security = docs.path("paths").path(path).path("post").path("security");
            assertThat(security.isArray() && security.isEmpty()).as(path + " sans sécurité").isTrue();
        }
    }

    @Test
    void everyControllerHasATag() throws Exception {
        JsonNode docs = docs();
        Set<String> tags = new HashSet<>();
        docs.path("tags").forEach(t -> tags.add(t.path("name").asText()));
        assertThat(tags).contains("Auth", "Users", "Simulation", "Mood Tracker", "Sondages", "Boîte à idées",
                "Bibliothèque", "Covoiturage", "Quiz", "Objectifs", "Secret Santa", "Événements", "Cantine",
                "Inventaire");
    }

    private static void collectRefs(JsonNode schema, JsonNode schemas, Set<String> out) {
        if (schema == null || schema.isMissingNode()) {
            return;
        }
        String ref = schema.path("$ref").asText();
        if (!ref.isEmpty()) {
            String name = ref.substring(ref.lastIndexOf('/') + 1);
            if (out.add(name)) {
                JsonNode target = schemas.path(name);
                collectRefs(target, schemas, out);
            }
            return;
        }
        collectRefs(schema.path("items"), schemas, out);
        for (Map.Entry<String, JsonNode> p : iterable(schema.path("properties").fields())) {
            collectRefs(p.getValue(), schemas, out);
        }
    }

    private static <T> Iterable<T> iterable(Iterator<T> it) {
        return () -> it;
    }
}
