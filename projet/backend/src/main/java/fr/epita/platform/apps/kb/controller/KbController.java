package fr.epita.platform.apps.kb.controller;

import fr.epita.platform.apps.kb.dto.KbDtos.*;
import fr.epita.platform.apps.kb.service.KbService;
import fr.epita.platform.common.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Base de connaissances", description = "Wiki interne : articles Markdown versionnés, relecture avant publication, avis.")
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KbController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String REV = "Réservé aux REVIEWER (FORBIDDEN)";
    private static final String A404 = "Article introuvable ou non publié (NOT_FOUND)";
    private static final String V404 = "Article ou version introuvable (NOT_FOUND)";

    private final KbService service;

    @GetMapping("/me/roles")
    @Operation(summary = "Mes rôles dans l'application", description = "Accès : authentifié. REVIEWER le cas échéant (un ADMIN l'a d'office).")
    @ApiResponse(responseCode = "200", description = "Rôles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<String> myRoles(@AuthenticationPrincipal AuthUser me) {
        return service.myRoles(me);
    }

    @GetMapping("/articles")
    @Operation(summary = "Articles publiés", description = "Accès : authentifié. Recherche plein texte (tous les mots dans titre, contenu ou tags), filtres catégorie, tag et `stale` (à vérifier).")
    @ApiResponse(responseCode = "200", description = "Articles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<ArticleSummary> articles(@RequestParam(required = false) String search, @RequestParam(required = false) String category,
                                         @RequestParam(required = false) String tag, @RequestParam(required = false) Boolean stale) {
        return service.search(search, category, tag, stale);
    }

    @GetMapping("/articles/{slug}")
    @Operation(summary = "Lire un article", description = "Accès : authentifié. Dernière version publiée (compte une consultation).")
    @ApiResponse(responseCode = "200", description = "Article")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = A404)
    public ArticleDto read(@AuthenticationPrincipal AuthUser me, @PathVariable String slug) {
        return service.read(me, slug);
    }

    @PostMapping("/articles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un article", description = "Accès : authentifié. Version 1 en brouillon ; slug unique.")
    @ApiResponse(responseCode = "201", description = "Version 1")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "409", description = "Slug déjà utilisé (SLUG_EXISTS)")
    public VersionDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody CreateRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/articles/{slug}/draft")
    @Operation(summary = "Enregistrer mon brouillon", description = "Accès : authentifié. Crée une nouvelle version en brouillon ; impossible pendant une relecture.")
    @ApiResponse(responseCode = "200", description = "Nouvelle version")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Article introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Relecture en cours (REVIEW_IN_PROGRESS)")
    public VersionDto draft(@AuthenticationPrincipal AuthUser me, @PathVariable String slug, @Valid @RequestBody DraftRequest req) {
        return service.saveDraft(me, slug, req);
    }

    @PostMapping("/articles/{slug}/submit")
    @Operation(summary = "Demander la relecture", description = "Accès : auteur de la dernière version (brouillon ou modifications demandées).")
    @ApiResponse(responseCode = "200", description = "Version en relecture")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "Pas l'auteur de la dernière version (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Article introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà en relecture ou publiée (INVALID_STATUS_TRANSITION)")
    public VersionDto submit(@AuthenticationPrincipal AuthUser me, @PathVariable String slug) {
        return service.submit(me, slug);
    }

    @GetMapping("/reviews")
    @Operation(summary = "Versions à relire", description = "Accès : REVIEWER. Plus anciennes d'abord.")
    @ApiResponse(responseCode = "200", description = "Versions")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = REV)
    public List<VersionDto> reviews(@AuthenticationPrincipal AuthUser me) {
        return service.reviews(me);
    }

    @PostMapping("/articles/{slug}/versions/{n}/publish")
    @Operation(summary = "Publier une version", description = "Accès : REVIEWER, jamais sur une version dont il est l'auteur.")
    @ApiResponse(responseCode = "200", description = "Version publiée")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = REV)
    @ApiResponse(responseCode = "404", description = V404)
    @ApiResponse(responseCode = "409", description = "SELF_REVIEW ou INVALID_STATUS_TRANSITION (pas en relecture)")
    public VersionDto publish(@AuthenticationPrincipal AuthUser me, @PathVariable String slug, @PathVariable int n) {
        return service.publish(me, slug, n);
    }

    @PostMapping("/articles/{slug}/versions/{n}/request-changes")
    @Operation(summary = "Demander des modifications", description = "Accès : REVIEWER. Commentaire obligatoire ; l'auteur corrige puis resoumet.")
    @ApiResponse(responseCode = "200", description = "Version à corriger")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = REV)
    @ApiResponse(responseCode = "404", description = V404)
    @ApiResponse(responseCode = "409", description = "Pas en relecture (INVALID_STATUS_TRANSITION)")
    public VersionDto requestChanges(@AuthenticationPrincipal AuthUser me, @PathVariable String slug, @PathVariable int n,
                                     @Valid @RequestBody ChangesRequest req) {
        return service.requestChanges(me, slug, n, req.comment());
    }

    @GetMapping("/articles/{slug}/versions")
    @Operation(summary = "Historique des versions", description = "Accès : authentifié. Un REVIEWER voit toutes les versions ; les autres, les versions publiées et les leurs.")
    @ApiResponse(responseCode = "200", description = "Versions, la plus récente d'abord")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Article introuvable (NOT_FOUND)")
    public List<VersionDto> versions(@AuthenticationPrincipal AuthUser me, @PathVariable String slug) {
        return service.history(me, slug);
    }

    @PostMapping("/articles/{slug}/versions/{n}/restore")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Restaurer une version", description = "Accès : authentifié. Crée une nouvelle version copiée de la version n, directement soumise à relecture.")
    @ApiResponse(responseCode = "201", description = "Nouvelle version en relecture")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = V404)
    @ApiResponse(responseCode = "409", description = "Relecture en cours (REVIEW_IN_PROGRESS)")
    public VersionDto restore(@AuthenticationPrincipal AuthUser me, @PathVariable String slug, @PathVariable int n) {
        return service.restore(me, slug, n);
    }

    @PutMapping("/articles/{slug}/feedback")
    @Operation(summary = "Donner mon avis", description = "Accès : authentifié. Un avis par personne et par article, modifiable.")
    @ApiResponse(responseCode = "200", description = "Article")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = A404)
    public ArticleDto feedback(@AuthenticationPrincipal AuthUser me, @PathVariable String slug, @Valid @RequestBody FeedbackRequest req) {
        return service.feedback(me, slug, req.helpful());
    }

    @GetMapping("/me/articles")
    @Operation(summary = "Mes articles", description = "Accès : authentifié. Articles dont j'ai rédigé au moins une version, avec le statut de la dernière.")
    @ApiResponse(responseCode = "200", description = "Articles")
    @ApiResponse(responseCode = "401", description = U401)
    public List<MyArticleDto> mine(@AuthenticationPrincipal AuthUser me) {
        return service.mine(me);
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques", description = "Accès : authentifié. 5 articles les plus consultés, les plus utiles, et ceux à vérifier (publiés il y a plus de 365 jours).")
    @ApiResponse(responseCode = "200", description = "Statistiques")
    @ApiResponse(responseCode = "401", description = U401)
    public StatsDto stats() {
        return service.stats();
    }
}
