package fr.epita.platform.apps.badge.controller;

import fr.epita.platform.apps.badge.dto.BadgeDtos.*;
import fr.epita.platform.apps.badge.service.BadgeService;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.user.dto.UserDtos.UserSummary;
import fr.epita.platform.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Tag(name = "Badges", description = "Badges gagnés selon l'activité, paliers, XP, profils, fil et classement.")
@RestController
@RequestMapping("/api/badges")
@RequiredArgsConstructor
public class BadgeController {

    private static final String U401 = "Jeton absent, invalide ou expiré (UNAUTHORIZED)";
    private static final String V400 = "Données invalides : détail par champ dans `fields` (VALIDATION_ERROR)";
    private static final String ADM = "Réservé aux ADMIN (FORBIDDEN)";

    private final BadgeService service;
    private final UserRepository users;

    @GetMapping
    @Operation(summary = "Catalogue", description = "Accès : authentifié. Les badges secrets non obtenus ont leur nom et leur règle masqués.")
    @ApiResponse(responseCode = "200", description = "Badges")
    @ApiResponse(responseCode = "401", description = U401)
    public List<BadgeDto> catalog(@AuthenticationPrincipal AuthUser me) {
        return service.catalog(me);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Créer un badge", description = "Accès : ADMIN. Règle COUNT / STREAK (seuil), TIERED (3 paliers croissants) ou MANUAL.")
    @ApiResponse(responseCode = "201", description = "Badge")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "409", description = "Code existant (BADGE_EXISTS)")
    public BadgeDto create(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody BadgeRequest req) {
        return service.create(me, req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un badge", description = "Accès : ADMIN. Les badges déjà obtenus ne sont jamais retirés.")
    @ApiResponse(responseCode = "200", description = "Badge")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Badge introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Code existant (BADGE_EXISTS)")
    public BadgeDto update(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody BadgeRequest req) {
        return service.update(me, id, req);
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistrer une activité", description = "Accès : authentifié (pour soi ; `userId` réservé aux ADMIN). Renvoie les badges et paliers nouvellement obtenus.")
    @ApiResponse(responseCode = "201", description = "Badges obtenus (souvent vide)")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = "`userId` d'un autre utilisateur sans être ADMIN (FORBIDDEN)")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    public List<EarnedDto> record(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody EventRequest req) {
        return service.record(me, req);
    }

    @GetMapping("/profiles/{userId}")
    @Operation(summary = "Profil", description = "Accès : authentifié. Niveau, XP, badges obtenus et verrouillés ; la progression chiffrée n'est visible que de l'intéressé (et des ADMIN).")
    @ApiResponse(responseCode = "200", description = "Profil")
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable (NOT_FOUND)")
    public ProfileDto profile(@AuthenticationPrincipal AuthUser me, @PathVariable UUID userId) {
        return service.profile(me, userId);
    }

    @GetMapping("/feed")
    @Operation(summary = "Fil d'actualité", description = "Accès : authentifié. Derniers badges gagnés, 20 par page ; `cursor` = `nextCursor` de la page précédente.")
    @ApiResponse(responseCode = "200", description = "Page du fil")
    @ApiResponse(responseCode = "400", description = "Curseur invalide (BAD_REQUEST)")
    @ApiResponse(responseCode = "401", description = U401)
    public FeedPage feed(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant cursor) {
        return service.feed(cursor);
    }

    @GetMapping("/leaderboard")
    @Operation(summary = "Classement", description = "Accès : authentifié. 50 premiers par XP.")
    @ApiResponse(responseCode = "200", description = "Classement")
    @ApiResponse(responseCode = "401", description = U401)
    public List<RankDto> leaderboard() {
        return service.leaderboard();
    }

    @PostMapping("/{id}/award")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Attribuer un badge", description = "Accès : ADMIN. Motif obligatoire ; pour un badge à paliers, le palier suivant.")
    @ApiResponse(responseCode = "201", description = "Badge attribué")
    @ApiResponse(responseCode = "400", description = V400)
    @ApiResponse(responseCode = "401", description = U401)
    @ApiResponse(responseCode = "403", description = ADM)
    @ApiResponse(responseCode = "404", description = "Badge ou utilisateur introuvable (NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Déjà obtenu (ALREADY_AWARDED)")
    public EarnedDto award(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id, @Valid @RequestBody AwardRequest req) {
        return service.awardManually(me, id, req);
    }

    @GetMapping("/people")
    @Operation(summary = "Collègues", description = "Accès : authentifié. Utilisateurs actifs.")
    @ApiResponse(responseCode = "200", description = "Utilisateurs")
    @ApiResponse(responseCode = "401", description = U401)
    public List<UserSummary> people() {
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(UserSummary::of).toList();
    }
}
