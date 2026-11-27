package fr.epita.platform.apps.form.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import fr.epita.platform.apps.form.entity.FieldType;
import fr.epita.platform.apps.form.entity.FormStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FormDtos {

    private FormDtos() {
    }

    public enum Operator { EQUALS, NOT_EQUALS, CONTAINS }

    @Schema(description = "Nouveau formulaire (brouillon, version 1 vide)")
    public record FormRequest(
            @Schema(description = "Titre, 150 caractères maximum", example = "Inscription au séminaire")
            @NotBlank @Size(max = 150) String title,
            @Schema(description = "Description, 1 000 caractères maximum", example = "Merci de répondre avant vendredi")
            @Size(max = 1000) String description,
            @Schema(description = "Réponses anonymes : l'auteur n'est pas enregistré", example = "false")
            boolean anonymous,
            @Schema(description = "Une seule réponse par utilisateur", example = "true")
            boolean oneResponsePerUser,
            @Schema(description = "Clôture automatique (ISO-8601, facultative)", example = "2026-12-20T18:00:00Z")
            Instant closesAt) {
    }

    @Schema(description = "Condition d'affichage : le champ n'est affiché (et validé) que si la condition sur un champ précédent est vraie")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Condition(
            @Schema(description = "Champ placé avant celui-ci", example = "vient")
            @NotBlank String fieldId,
            @Schema(description = "EQUALS, NOT_EQUALS ou CONTAINS", example = "EQUALS")
            @NotNull Operator operator,
            @Schema(description = "Valeur comparée (texte)", example = "oui")
            @NotNull String value) {
    }

    @Schema(description = """
            Champ. `config` selon le type : TEXT {minLength, maxLength, pattern} ; LONG_TEXT {maxLength} ; NUMBER {min, max, step} ; \
            DATE {min, max} ; SELECT / RADIO {options: [{value, label}]} ; CHECKBOXES {options, minSelected, maxSelected} ; RATING {max}.""")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FieldDef(
            @Schema(description = "Identifiant unique dans le formulaire (lettre puis lettres, chiffres ou _)", example = "regime")
            String id,
            @Schema(description = "Type de champ", example = "SELECT")
            FieldType type,
            @Schema(description = "Libellé affiché", example = "Régime alimentaire")
            String label,
            @Schema(description = "Aide sous le champ (facultative)", example = "Pour le traiteur")
            String help,
            @Schema(description = "Réponse obligatoire", example = "true")
            boolean required,
            @Schema(description = "Configuration propre au type (voir la description du schéma)")
            Map<String, Object> config,
            @Schema(description = "Condition d'affichage (facultative)")
            @Valid Condition condition) {
    }

    @Schema(description = "Structure du brouillon : liste ordonnée des champs (50 maximum)")
    public record StructureRequest(
            @Schema(description = "Champs dans l'ordre d'affichage")
            @NotNull @Size(max = 50) List<@Valid FieldDef> fields) {
    }

    @Schema(description = "Réponses {fieldId: valeur} : texte, nombre, ou liste de valeurs pour CHECKBOXES")
    public record AnswersRequest(
            @Schema(description = "Valeurs par identifiant de champ", example = "{\"vient\": \"oui\", \"regime\": \"vegetarien\"}")
            @NotNull Map<String, JsonNode> answers) {
    }

    @Schema(description = "Formulaire dans une liste")
    public record FormSummary(UUID id, String title, FormStatus status, String ownerName, boolean mine, boolean anonymous,
                              Integer publishedVersion, Integer draftVersion, long responses, Instant closesAt, boolean answered) {
    }

    @Schema(description = "Formulaire et structure de la version courante (brouillon pour le créateur s'il existe, sinon version publiée)")
    public record FormDto(UUID id, String title, String description, boolean anonymous, boolean oneResponsePerUser, Instant closesAt,
                          FormStatus status, boolean mine, int version, boolean editable, Integer publishedVersion, Integer draftVersion,
                          List<FieldDef> fields, boolean answered) {
    }

    @Schema(description = "Résultat d'une validation : erreurs par champ (`answers.<fieldId>`), champs masqués par leur condition")
    public record ValidationResult(boolean valid, Map<String, String> errors, List<String> hiddenFields) {
    }

    @Schema(description = "Réponse enregistrée ; `respondentName` null pour un formulaire anonyme")
    public record ResponseDto(UUID id, int version, String respondentName, Instant submittedAt, Map<String, Object> answers) {
    }

    @Schema(description = "Statistiques d'un champ : nombre de réponses, répartition (choix), moyenne / min / max (nombres, notes)")
    public record FieldStats(String fieldId, String label, FieldType type, long answered, Map<String, Long> distribution,
                             Double average, Double min, Double max, List<String> samples) {
    }

    @Schema(description = "Statistiques d'une version")
    public record StatsDto(int version, long responses, List<FieldStats> fields) {
    }

    @Schema(description = "Type de champ et clés de configuration acceptées")
    public record FieldTypeDto(FieldType type, String label, List<String> configKeys, String answer) {
    }
}
