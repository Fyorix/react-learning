package fr.epita.platform.apps.badge.dto;

import fr.epita.platform.apps.badge.entity.Rarity;
import fr.epita.platform.apps.badge.entity.RuleType;
import fr.epita.platform.apps.badge.entity.Tier;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BadgeDtos {

    private BadgeDtos() {
    }

    @Schema(description = "Badge (ADMIN). COUNT / STREAK : `event` et `threshold` ; TIERED : `event` et trois paliers croissants ; MANUAL : aucune règle.")
    public record BadgeRequest(
            @Schema(description = "Code unique en majuscules", example = "KUDO_GIVER")
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,39}") String code,
            @Schema(description = "Nom, 80 caractères maximum", example = "Généreux")
            @NotBlank @Size(max = 80) String name,
            @Schema(description = "Icône (emoji)", example = "🎁")
            @NotBlank @Size(max = 16) String icon,
            @Schema(description = "Description, 300 caractères maximum", example = "Envoyer 10 kudos")
            @NotBlank @Size(max = 300) String description,
            @Schema(description = "COMMON (10 XP), RARE (25), EPIC (50) ou LEGENDARY (100)", example = "RARE")
            @NotNull Rarity rarity,
            @Schema(description = "Secret : nom et règle masqués tant qu'il n'est pas obtenu", example = "false")
            boolean secret,
            @Schema(description = "COUNT, STREAK, TIERED ou MANUAL", example = "COUNT")
            @NotNull RuleType ruleType,
            @Schema(description = "Type d'événement compté", example = "KUDO_SENT")
            @Pattern(regexp = "[A-Z][A-Z0-9_]{1,39}") String event,
            @Schema(description = "Seuil (COUNT, STREAK)", example = "10")
            @Min(1) @Max(100000) Integer threshold,
            @Schema(description = "Palier bronze (TIERED)", example = "5")
            @Min(1) Integer tierBronze,
            @Schema(description = "Palier argent (TIERED)", example = "20")
            @Min(1) Integer tierSilver,
            @Schema(description = "Palier or (TIERED)", example = "50")
            @Min(1) Integer tierGold) {
    }

    @Schema(description = "Activité enregistrée")
    public record EventRequest(
            @Schema(description = "Type d'activité en majuscules", example = "KUDO_SENT")
            @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{1,39}") String type,
            @Schema(description = "Date de l'activité (ISO-8601, pas dans le futur) ; maintenant par défaut", example = "2026-11-20T09:00:00Z")
            Instant occurredAt,
            @Schema(description = "Utilisateur concerné (ADMIN uniquement, pour la démonstration) ; soi-même par défaut", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            UUID userId) {
    }

    @Schema(description = "Attribution manuelle (ADMIN)")
    public record AwardRequest(
            @Schema(description = "Bénéficiaire", example = "9b76d11e-ec02-474c-8ca9-8886c4bfbb7d")
            @NotNull UUID userId,
            @Schema(description = "Motif obligatoire, 300 caractères maximum", example = "Organisation du séminaire")
            @NotBlank @Size(max = 300) String reason) {
    }

    @Schema(description = "Badge du catalogue ; pour un badge secret non obtenu : nom, description et règle masqués")
    public record BadgeDto(UUID id, String code, String name, String icon, String description, Rarity rarity, boolean secret,
                           RuleType ruleType, String event, Integer threshold, Integer tierBronze, Integer tierSilver, Integer tierGold,
                           int points, long holders, boolean hidden) {
    }

    @Schema(description = "Badge (ou palier) obtenu")
    public record EarnedDto(UUID badgeId, String code, String name, String icon, Rarity rarity, Tier tier, int points, String reason, Instant awardedAt) {
    }

    @Schema(description = "Badge verrouillé et progression (« 7 / 10 ») ; masquée pour un secret")
    public record LockedDto(UUID badgeId, String name, String icon, Rarity rarity, String description, Tier nextTier, Integer current, Integer target, boolean hidden) {
    }

    @Schema(description = "Profil : niveau = 1 + XP / 100")
    public record ProfileDto(UUID userId, String name, int level, long xp, int xpIntoLevel, List<EarnedDto> earned, List<LockedDto> locked) {
    }

    @Schema(description = "Entrée du fil d'actualité")
    public record FeedItem(UUID awardId, UUID userId, String userName, String badgeName, String icon, Rarity rarity, Tier tier, Instant at) {
    }

    @Schema(description = "Page du fil ; `nextCursor` à repasser pour la suite (null en fin de fil)")
    public record FeedPage(List<FeedItem> items, Instant nextCursor) {
    }

    @Schema(description = "Ligne du classement")
    public record RankDto(int rank, UUID userId, String name, long xp, int level) {
    }
}
