package fr.epita.platform.apps.jargon.dto;

import fr.epita.platform.apps.jargon.entity.ContributionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class JargonDtos {

    private JargonDtos() {
    }

    @Schema(description = "Proposition d'un terme avec sa première définition")
    public record TermRequest(
            @Schema(description = "Terme ou acronyme, unique (casse et accents ignorés), 60 caractères maximum", example = "MEP")
            @NotBlank @Size(max = 60) String term,
            @Schema(description = "Forme développée (facultative), 200 caractères maximum", example = "Mise en production")
            @Size(max = 200) String expansion,
            @Schema(description = "Équipe d'origine (facultative), 60 caractères maximum", example = "DevOps")
            @Size(max = 60) String teamOfOrigin,
            @Schema(description = "Première définition, 2 000 caractères maximum", example = "Déploiement d'une version sur l'environnement de production.")
            @NotBlank @Size(max = 2000) String definition,
            @Schema(description = "Exemple d'usage (facultatif), 500 caractères maximum", example = "La MEP est prévue jeudi à 18 h.")
            @Size(max = 500) String example,
            @Schema(description = "Termes liés (publiés)")
            List<UUID> relatedTermIds) {
    }

    @Schema(description = "Proposition d'une définition supplémentaire")
    public record DefinitionRequest(
            @Schema(description = "Définition, 2 000 caractères maximum", example = "Synonyme de release dans l'équipe mobile.")
            @NotBlank @Size(max = 2000) String text,
            @Schema(description = "Exemple d'usage (facultatif), 500 caractères maximum", example = "On fait la MEP de l'app demain.")
            @Size(max = 500) String example) {
    }

    @Schema(description = "Refus d'une contribution (motif obligatoire)")
    public record RejectRequest(
            @Schema(description = "Motif, 500 caractères maximum", example = "Doublon de « Mise en prod »")
            @NotBlank @Size(max = 500) String reason) {
    }

    @Schema(description = "Suggestion d'autocomplétion")
    public record Suggestion(UUID id, String term, String expansion) {
    }

    @Schema(description = "Terme du glossaire")
    public record TermSummary(UUID id, String term, String expansion, String teamOfOrigin, int definitions) {
    }

    @Schema(description = "Définition publiée, avec ses votes « utile »")
    public record DefinitionDto(UUID id, String text, String example, String authorName, long helpful, boolean votedByMe, boolean mine) {
    }

    @Schema(description = "Fiche d'un terme : définitions triées par votes, termes liés")
    public record TermDto(UUID id, String term, String expansion, String teamOfOrigin, List<DefinitionDto> definitions, List<Suggestion> related) {
    }

    @Schema(description = "Contribution : `kind` TERM (nouveau terme et sa définition) ou DEFINITION")
    public record ContributionDto(UUID id, String kind, UUID termId, String term, String expansion, String text, String example,
                                  String authorName, ContributionStatus status, String reason, Instant createdAt) {
    }

    @Schema(description = "Recherche fréquente sans résultat")
    public record MissingDto(String query, int hits, Instant lastAt) {
    }
}
