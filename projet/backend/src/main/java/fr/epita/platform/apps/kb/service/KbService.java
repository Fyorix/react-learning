package fr.epita.platform.apps.kb.service;

import fr.epita.platform.apps.kb.dto.KbDtos.*;
import fr.epita.platform.apps.kb.entity.*;
import fr.epita.platform.apps.kb.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Versions : chaque enregistrement crée une version ; une seule version en relecture à la fois.
 * L'article reste visible dans sa dernière version publiée pendant la relecture d'une nouvelle version.
 */
@Service
@RequiredArgsConstructor
public class KbService {

    public static final String ROLES = "kb_roles";
    public static final String REVIEWER = "REVIEWER";
    static final Duration STALE_AFTER = Duration.ofDays(365);

    private final KbArticleRepository articles;
    private final KbVersionRepository versions;
    private final KbFeedbackRepository feedback;
    private final AppRoles roles;
    private final UserLookup userLookup;
    private final Clock clock;

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(REVIEWER));
    }

    // ---------- lecture ----------

    /** Recherche plein texte (titre, contenu, tags) sur la version publiée. */
    @Transactional(readOnly = true)
    public List<ArticleSummary> search(String search, String category, String tag, Boolean stale) {
        List<String> terms = search == null ? List.of() : Arrays.stream(search.toLowerCase().split("\\s+")).filter(s -> !s.isBlank()).toList();
        return articles.findByPublishedVersionNotNull().stream()
                .map(a -> Map.entry(a, published(a)))
                .filter(e -> category == null || category.isBlank() || e.getValue().getCategory().equalsIgnoreCase(category))
                .filter(e -> tag == null || tag.isBlank() || e.getValue().tagList().contains(tag.toLowerCase()))
                .filter(e -> stale == null || isStale(e.getKey()) == stale)
                .filter(e -> terms.stream().allMatch(t -> (e.getValue().getTitle() + " " + e.getValue().getContent() + " " + e.getValue().getTags()).toLowerCase().contains(t)))
                .map(e -> summary(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(ArticleSummary::title, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public ArticleDto read(AuthUser me, String slug) {
        KbArticle a = article(slug);
        if (a.getPublishedVersion() == null) {
            throw ApiException.notFound("Article");
        }
        articles.incrementViews(a.getId());
        KbVersion v = published(a);
        long[] fb = feedbackCounts(a);
        Boolean mine = feedback.findById(new KbFeedback.Key(a.getId(), me.id())).map(KbFeedback::isHelpful).orElse(null);
        Integer pending = versions.findFirstByArticleIdOrderByNumberDesc(a.getId()).filter(x -> x.getStatus() == VersionStatus.IN_REVIEW)
                .map(KbVersion::getNumber).orElse(null);
        return new ArticleDto(a.getId(), a.getSlug(), v.getNumber(), v.getTitle(), v.getCategory(), v.tagList(), v.getContent(),
                v.getAuthorId() == null ? null : userLookup.name(v.getAuthorId()), a.getPublishedAt(), a.getViews() + 1, fb[0], fb[1], mine,
                isStale(a), pending);
    }

    /** Historique : un relecteur voit tout ; les autres voient les versions publiées et les leurs. */
    @Transactional(readOnly = true)
    public List<VersionDto> history(AuthUser me, String slug) {
        KbArticle a = article(slug);
        boolean reviewer = roles.has(ROLES, me, REVIEWER);
        List<VersionDto> list = versions.findByArticleIdOrderByNumberDesc(a.getId()).stream()
                .filter(v -> reviewer || v.getStatus() == VersionStatus.PUBLISHED || me.id().equals(v.getAuthorId()))
                .map(v -> toDto(a, v)).toList();
        if (list.isEmpty()) {
            throw ApiException.notFound("Article");
        }
        return list;
    }

    // ---------- rédaction ----------

    @Transactional
    public VersionDto create(AuthUser me, CreateRequest req) {
        if (articles.existsBySlug(req.slug())) {
            throw ApiException.conflict("SLUG_EXISTS", "Un article utilise déjà cet identifiant d'URL");
        }
        KbArticle a = new KbArticle();
        a.setSlug(req.slug());
        a.setCreatedBy(me.id());
        articles.saveAndFlush(a);
        return toDto(a, newVersion(a, me, req.title(), req.category(), req.tags(), req.content(), VersionStatus.DRAFT, null));
    }

    @Transactional
    public VersionDto saveDraft(AuthUser me, String slug, DraftRequest req) {
        KbArticle a = articles.lock(slug).orElseThrow(() -> ApiException.notFound("Article"));
        requireNoReview(a);
        return toDto(a, newVersion(a, me, req.title(), req.category(), req.tags(), req.content(), VersionStatus.DRAFT, null));
    }

    /** Soumet la dernière version (brouillon ou modifications demandées) de l'utilisateur. */
    @Transactional
    public VersionDto submit(AuthUser me, String slug) {
        KbArticle a = articles.lock(slug).orElseThrow(() -> ApiException.notFound("Article"));
        KbVersion v = versions.findFirstByArticleIdOrderByNumberDesc(a.getId()).orElseThrow();
        if (!me.id().equals(v.getAuthorId())) {
            throw ApiException.forbidden("Seul l'auteur de la dernière version peut la soumettre");
        }
        if (v.getStatus() != VersionStatus.DRAFT && v.getStatus() != VersionStatus.CHANGES_REQUESTED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "La dernière version est déjà " + v.getStatus());
        }
        v.setStatus(VersionStatus.IN_REVIEW);
        v.setReviewComment(null);
        return toDto(a, versions.saveAndFlush(v));
    }

    /** Restaurer une ancienne version : nouvelle version copiée, directement soumise à relecture. */
    @Transactional
    public VersionDto restore(AuthUser me, String slug, int number) {
        KbArticle a = articles.lock(slug).orElseThrow(() -> ApiException.notFound("Article"));
        requireNoReview(a);
        KbVersion old = version(a, number);
        return toDto(a, newVersion(a, me, old.getTitle(), old.getCategory(), old.tagList(), old.getContent(), VersionStatus.IN_REVIEW, number));
    }

    // ---------- relecture ----------

    @Transactional(readOnly = true)
    public List<VersionDto> reviews(AuthUser me) {
        roles.require(ROLES, me, REVIEWER);
        return versions.findByStatusOrderByCreatedAtAsc(VersionStatus.IN_REVIEW).stream()
                .map(v -> toDto(articles.findById(v.getArticleId()).orElseThrow(), v)).toList();
    }

    @Transactional
    public VersionDto publish(AuthUser me, String slug, int number) {
        roles.require(ROLES, me, REVIEWER);
        KbArticle a = articles.lock(slug).orElseThrow(() -> ApiException.notFound("Article"));
        KbVersion v = inReview(a, number);
        if (me.id().equals(v.getAuthorId())) {
            throw ApiException.conflict("SELF_REVIEW", "Vous ne pouvez pas publier une version dont vous êtes l'auteur");
        }
        v.setStatus(VersionStatus.PUBLISHED);
        v.setReviewedBy(me.id());
        a.setPublishedVersion(number);
        a.setPublishedAt(clock.instant());
        articles.save(a);
        return toDto(a, versions.saveAndFlush(v));
    }

    @Transactional
    public VersionDto requestChanges(AuthUser me, String slug, int number, String comment) {
        roles.require(ROLES, me, REVIEWER);
        KbArticle a = articles.lock(slug).orElseThrow(() -> ApiException.notFound("Article"));
        KbVersion v = inReview(a, number);
        v.setStatus(VersionStatus.CHANGES_REQUESTED);
        v.setReviewComment(comment.trim());
        v.setReviewedBy(me.id());
        return toDto(a, versions.saveAndFlush(v));
    }

    // ---------- avis et tableaux de bord ----------

    @Transactional
    public ArticleDto feedback(AuthUser me, String slug, boolean helpful) {
        KbArticle a = article(slug);
        if (a.getPublishedVersion() == null) {
            throw ApiException.notFound("Article");
        }
        KbFeedback f = feedback.findById(new KbFeedback.Key(a.getId(), me.id())).orElseGet(() -> {
            KbFeedback n = new KbFeedback();
            n.setId(new KbFeedback.Key(a.getId(), me.id()));
            return n;
        });
        f.setHelpful(helpful);
        feedback.saveAndFlush(f);
        KbVersion v = published(a);
        long[] fb = feedbackCounts(a);
        return new ArticleDto(a.getId(), a.getSlug(), v.getNumber(), v.getTitle(), v.getCategory(), v.tagList(), v.getContent(),
                v.getAuthorId() == null ? null : userLookup.name(v.getAuthorId()), a.getPublishedAt(), a.getViews(), fb[0], fb[1], helpful, isStale(a), null);
    }

    @Transactional(readOnly = true)
    public List<MyArticleDto> mine(AuthUser me) {
        Set<UUID> ids = versions.findByAuthorIdOrderByCreatedAtDesc(me.id()).stream().map(KbVersion::getArticleId).collect(Collectors.toCollection(LinkedHashSet::new));
        return ids.stream().map(id -> {
            KbArticle a = articles.findById(id).orElseThrow();
            KbVersion latest = versions.findFirstByArticleIdOrderByNumberDesc(id).orElseThrow();
            return new MyArticleDto(a.getSlug(), latest.getTitle(), latest.getNumber(), latest.getStatus(), a.getPublishedVersion(), latest.getReviewComment());
        }).toList();
    }

    @Transactional(readOnly = true)
    public StatsDto stats() {
        List<ArticleSummary> all = search(null, null, null, null);
        return new StatsDto(
                all.stream().sorted(Comparator.comparingInt(ArticleSummary::views).reversed()).limit(5).toList(),
                all.stream().filter(s -> s.helpful() + s.notHelpful() > 0)
                        .sorted(Comparator.comparingDouble((ArticleSummary s) -> (double) s.helpful() / (s.helpful() + s.notHelpful())).reversed()
                                .thenComparing(ArticleSummary::helpful, Comparator.reverseOrder())).limit(5).toList(),
                all.stream().filter(ArticleSummary::stale).toList());
    }

    // ---------- outils ----------

    private KbVersion newVersion(KbArticle a, AuthUser me, String title, String category, List<String> tags, String content, VersionStatus status, Integer restoredFrom) {
        int next = versions.findFirstByArticleIdOrderByNumberDesc(a.getId()).map(KbVersion::getNumber).orElse(0) + 1;
        KbVersion v = new KbVersion();
        v.setArticleId(a.getId());
        v.setNumber(next);
        v.setTitle(title.trim());
        v.setCategory(category.trim());
        v.setTags(tags == null ? "" : tags.stream().map(t -> t.trim().toLowerCase()).filter(t -> !t.isEmpty()).distinct()
                .peek(t -> {
                    if (t.contains(",")) {
                        throw ValidationException.field("tags", "un tag ne contient pas de virgule");
                    }
                }).collect(Collectors.joining(",")));
        v.setContent(content);
        v.setStatus(status);
        v.setAuthorId(me.id());
        v.setRestoredFrom(restoredFrom);
        return versions.saveAndFlush(v);
    }

    private void requireNoReview(KbArticle a) {
        if (versions.existsByArticleIdAndStatus(a.getId(), VersionStatus.IN_REVIEW)) {
            throw ApiException.conflict("REVIEW_IN_PROGRESS", "Une version est en cours de relecture");
        }
    }

    private KbVersion inReview(KbArticle a, int number) {
        KbVersion v = version(a, number);
        if (v.getStatus() != VersionStatus.IN_REVIEW) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "La version " + number + " n'est pas en relecture");
        }
        return v;
    }

    private KbArticle article(String slug) {
        return articles.findBySlug(slug).orElseThrow(() -> ApiException.notFound("Article"));
    }

    private KbVersion version(KbArticle a, int number) {
        return versions.findByArticleIdAndNumber(a.getId(), number).orElseThrow(() -> ApiException.notFound("Version"));
    }

    private KbVersion published(KbArticle a) {
        return version(a, a.getPublishedVersion());
    }

    private boolean isStale(KbArticle a) {
        return a.getPublishedAt() != null && a.getPublishedAt().isBefore(clock.instant().minus(STALE_AFTER));
    }

    private long[] feedbackCounts(KbArticle a) {
        List<KbFeedback> fs = feedback.findByIdArticleId(a.getId());
        long yes = fs.stream().filter(KbFeedback::isHelpful).count();
        return new long[]{yes, fs.size() - yes};
    }

    private ArticleSummary summary(KbArticle a, KbVersion v) {
        long[] fb = feedbackCounts(a);
        String plain = v.getContent().replaceAll("[#*_`>\\[\\]()-]", " ").replaceAll("\\s+", " ").trim();
        return new ArticleSummary(a.getId(), a.getSlug(), v.getTitle(), v.getCategory(), v.tagList(),
                plain.length() > 160 ? plain.substring(0, 160) + "…" : plain, a.getPublishedAt(), a.getViews(), fb[0], fb[1], isStale(a));
    }

    private VersionDto toDto(KbArticle a, KbVersion v) {
        return new VersionDto(v.getId(), a.getSlug(), v.getNumber(), v.getTitle(), v.getCategory(), v.tagList(), v.getContent(), v.getStatus(),
                v.getAuthorId(), v.getAuthorId() == null ? null : userLookup.name(v.getAuthorId()), v.getReviewComment(),
                v.getReviewedBy() == null ? null : userLookup.name(v.getReviewedBy()), v.getRestoredFrom(), v.getCreatedAt(),
                Objects.equals(a.getPublishedVersion(), v.getNumber()));
    }
}
