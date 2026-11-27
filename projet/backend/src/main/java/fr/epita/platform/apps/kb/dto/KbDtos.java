package fr.epita.platform.apps.kb.dto;

import fr.epita.platform.apps.kb.entity.VersionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class KbDtos {

    private KbDtos() {
    }

    @Schema(description = "Nouvel article (version 1 en brouillon)")
    public record CreateRequest(
            @Schema(description = "Identifiant d'URL unique : minuscules, chiffres et tirets", example = "configurer-le-vpn")
            @NotBlank @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") @Size(max = 120) String slug,
            @Schema(description = "Titre, 200 caractères maximum", example = "Configurer le VPN")
            @NotBlank @Size(max = 200) String title,
            @Schema(description = "Catégorie, 60 caractères maximum", example = "Informatique")
            @NotBlank @Size(max = 60) String category,
            @Schema(description = "Tags (10 maximum, 30 caractères chacun)", example = "[\"vpn\", \"télétravail\"]")
            @Size(max = 10) List<@Size(max = 30) String> tags,
            @Schema(description = "Contenu Markdown, 100 000 caractères maximum", example = "## Étapes\n1. Installer le client…")
            @NotBlank @Size(max = 100000) String content) {
    }

    @Schema(description = "Brouillon : chaque enregistrement crée une nouvelle version")
    public record DraftRequest(
            @Schema(description = "Titre, 200 caractères maximum", example = "Configurer le VPN")
            @NotBlank @Size(max = 200) String title,
            @Schema(description = "Catégorie, 60 caractères maximum", example = "Informatique")
            @NotBlank @Size(max = 60) String category,
            @Schema(description = "Tags (10 maximum)", example = "[\"vpn\"]")
            @Size(max = 10) List<@Size(max = 30) String> tags,
            @Schema(description = "Contenu Markdown", example = "## Étapes…")
            @NotBlank @Size(max = 100000) String content) {
    }

    @Schema(description = "Demande de modifications")
    public record ChangesRequest(
            @Schema(description = "Commentaire obligatoire, 2 000 caractères maximum", example = "Préciser la version du client VPN")
            @NotBlank @Size(max = 2000) String comment) {
    }

    @Schema(description = "Avis sur un article")
    public record FeedbackRequest(
            @Schema(description = "L'article m'a été utile", example = "true")
            @NotNull Boolean helpful) {
    }

    @Schema(description = "Article publié dans une liste ; `stale` = publié sans modification depuis plus de 365 jours")
    public record ArticleSummary(UUID id, String slug, String title, String category, List<String> tags, String excerpt,
                                 Instant publishedAt, int views, long helpful, long notHelpful, boolean stale) {
    }

    @Schema(description = "Dernière version publiée d'un article ; `pendingVersion` : numéro d'une version en relecture")
    public record ArticleDto(UUID id, String slug, int version, String title, String category, List<String> tags, String content,
                             String authorName, Instant publishedAt, int views, long helpful, long notHelpful, Boolean myFeedback,
                             boolean stale, Integer pendingVersion) {
    }

    @Schema(description = "Version d'un article")
    public record VersionDto(UUID id, String slug, int number, String title, String category, List<String> tags, String content,
                             VersionStatus status, UUID authorId, String authorName, String reviewComment, String reviewedByName,
                             Integer restoredFrom, Instant createdAt, boolean current) {
    }

    @Schema(description = "Un de mes articles : dernière version et version publiée")
    public record MyArticleDto(String slug, String title, int latestVersion, VersionStatus latestStatus, Integer publishedVersion, String reviewComment) {
    }

    @Schema(description = "Statistiques : les plus consultés, les plus utiles, à vérifier")
    public record StatsDto(List<ArticleSummary> mostViewed, List<ArticleSummary> mostHelpful, List<ArticleSummary> stale) {
    }
}
