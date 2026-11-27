package fr.epita.platform.apps.badge.service;

import fr.epita.platform.apps.badge.dto.BadgeDtos.*;
import fr.epita.platform.apps.badge.entity.*;
import fr.epita.platform.apps.badge.repository.ActivityEventRepository;
import fr.epita.platform.apps.badge.repository.AwardRepository;
import fr.epita.platform.apps.badge.repository.BadgeRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * À chaque activité, les compteurs sont recalculés et les badges atteints attribués dans la même transaction.
 * Une attribution n'est jamais retirée, même si la règle du badge change ensuite (unicité badge + palier en base).
 */
@Service
@RequiredArgsConstructor
public class BadgeService {

    static final int FEED_PAGE = 20;
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final BadgeRepository badges;
    private final ActivityEventRepository events;
    private final AwardRepository awards;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- catalogue ----------

    @Transactional(readOnly = true)
    public List<BadgeDto> catalog(AuthUser me) {
        Set<UUID> mine = awards.findByUserIdOrderByCreatedAtDesc(me.id()).stream().map(Award::getBadgeId).collect(Collectors.toSet());
        Map<UUID, Long> holders = awards.findAll().stream().collect(Collectors.groupingBy(Award::getBadgeId,
                Collectors.collectingAndThen(Collectors.mapping(Award::getUserId, Collectors.toSet()), s -> (long) s.size())));
        return badges.findAllByOrderByNameAsc().stream()
                .map(b -> toDto(b, b.isSecret() && !me.isAdmin() && !mine.contains(b.getId()), holders.getOrDefault(b.getId(), 0L))).toList();
    }

    @Transactional
    public BadgeDto create(AuthUser me, BadgeRequest req) {
        me.requireAdmin();
        if (badges.existsByCode(req.code())) {
            throw ApiException.conflict("BADGE_EXISTS", "Ce code de badge existe déjà");
        }
        Badge b = new Badge();
        b.setCode(req.code());
        apply(b, req);
        return toDto(badges.saveAndFlush(b), false, 0);
    }

    /** La modification d'une règle ne retire jamais un badge déjà obtenu. */
    @Transactional
    public BadgeDto update(AuthUser me, UUID id, BadgeRequest req) {
        me.requireAdmin();
        Badge b = badges.findById(id).orElseThrow(() -> ApiException.notFound("Badge"));
        if (!b.getCode().equals(req.code()) && badges.existsByCode(req.code())) {
            throw ApiException.conflict("BADGE_EXISTS", "Ce code de badge existe déjà");
        }
        b.setCode(req.code());
        apply(b, req);
        return toDto(badges.saveAndFlush(b), false, 0);
    }

    private void apply(Badge b, BadgeRequest req) {
        var errors = new ValidationException.Collector();
        switch (req.ruleType()) {
            case COUNT, STREAK -> {
                errors.check(req.event() != null, "event", "obligatoire pour une règle " + req.ruleType());
                errors.check(req.threshold() != null, "threshold", "obligatoire pour une règle " + req.ruleType());
            }
            case TIERED -> {
                errors.check(req.event() != null, "event", "obligatoire pour un badge à paliers");
                boolean ok = req.tierBronze() != null && req.tierSilver() != null && req.tierGold() != null
                        && req.tierBronze() < req.tierSilver() && req.tierSilver() < req.tierGold();
                errors.check(ok, "tierGold", "trois paliers strictement croissants (bronze < argent < or)");
            }
            case MANUAL -> {
            }
        }
        errors.throwIfAny();
        b.setName(req.name().trim());
        b.setIcon(req.icon().trim());
        b.setDescription(req.description().trim());
        b.setRarity(req.rarity());
        b.setSecret(req.secret());
        b.setRuleType(req.ruleType());
        b.setEvent(req.ruleType() == RuleType.MANUAL ? null : req.event());
        b.setThreshold(req.ruleType() == RuleType.COUNT || req.ruleType() == RuleType.STREAK ? req.threshold() : null);
        b.setTierBronze(req.ruleType() == RuleType.TIERED ? req.tierBronze() : null);
        b.setTierSilver(req.ruleType() == RuleType.TIERED ? req.tierSilver() : null);
        b.setTierGold(req.ruleType() == RuleType.TIERED ? req.tierGold() : null);
    }

    // ---------- activités ----------

    @Transactional
    public List<EarnedDto> record(AuthUser me, EventRequest req) {
        UUID target = req.userId() == null ? me.id() : req.userId();
        if (!target.equals(me.id())) {
            me.requireAdmin();
            userLookup.require(target);
        }
        Instant at = req.occurredAt() == null ? clock.instant() : req.occurredAt();
        if (at.isAfter(clock.instant())) {
            throw ValidationException.field("occurredAt", "ne doit pas être dans le futur");
        }
        ActivityEvent e = new ActivityEvent();
        e.setUserId(target);
        e.setType(req.type());
        e.setOccurredAt(at);
        events.saveAndFlush(e);
        List<EarnedDto> earned = new ArrayList<>();
        for (Badge b : badges.findByEvent(req.type())) {
            for (Tier tier : reached(b, target)) {
                if (!awards.existsByBadgeIdAndUserIdAndTier(b.getId(), target, tier)) {
                    earned.add(toDto(b, award(b, target, tier, null, null)));
                }
            }
        }
        return earned;
    }

    /** Paliers atteints selon la règle actuelle du badge. */
    private List<Tier> reached(Badge b, UUID userId) {
        return switch (b.getRuleType()) {
            case COUNT -> events.countByUserIdAndType(userId, b.getEvent()) >= b.getThreshold() ? List.of(Tier.NONE) : List.of();
            case STREAK -> longestStreak(userId, b.getEvent()) >= b.getThreshold() ? List.of(Tier.NONE) : List.of();
            case TIERED -> {
                long n = events.countByUserIdAndType(userId, b.getEvent());
                List<Tier> tiers = new ArrayList<>();
                if (n >= b.getTierBronze()) {
                    tiers.add(Tier.BRONZE);
                }
                if (n >= b.getTierSilver()) {
                    tiers.add(Tier.SILVER);
                }
                if (n >= b.getTierGold()) {
                    tiers.add(Tier.GOLD);
                }
                yield tiers;
            }
            case MANUAL -> List.of();
        };
    }

    /** Plus longue série de jours consécutifs (heure de Paris) avec au moins un événement du type. */
    int longestStreak(UUID userId, String type) {
        TreeSet<LocalDate> days = events.findByUserIdAndType(userId, type).stream()
                .map(e -> e.getOccurredAt().atZone(ZONE).toLocalDate()).collect(Collectors.toCollection(TreeSet::new));
        int best = 0;
        int current = 0;
        LocalDate previous = null;
        for (LocalDate d : days) {
            current = previous != null && previous.plusDays(1).equals(d) ? current + 1 : 1;
            best = Math.max(best, current);
            previous = d;
        }
        return best;
    }

    private Award award(Badge b, UUID userId, Tier tier, String reason, UUID by) {
        Award a = new Award();
        a.setBadgeId(b.getId());
        a.setUserId(userId);
        a.setTier(tier);
        a.setPoints(b.getRarity().points);
        a.setReason(reason);
        a.setAwardedBy(by);
        return awards.saveAndFlush(a);
    }

    @Transactional
    public EarnedDto awardManually(AuthUser me, UUID badgeId, AwardRequest req) {
        me.requireAdmin();
        Badge b = badges.findById(badgeId).orElseThrow(() -> ApiException.notFound("Badge"));
        userLookup.require(req.userId());
        Tier tier = b.getRuleType() == RuleType.TIERED ? nextTier(b, req.userId()) : Tier.NONE;
        if (tier == null || awards.existsByBadgeIdAndUserIdAndTier(badgeId, req.userId(), tier)) {
            throw ApiException.conflict("ALREADY_AWARDED", "Ce badge est déjà obtenu");
        }
        return toDto(b, award(b, req.userId(), tier, req.reason().trim(), me.id()));
    }

    private Tier nextTier(Badge b, UUID userId) {
        for (Tier t : List.of(Tier.BRONZE, Tier.SILVER, Tier.GOLD)) {
            if (!awards.existsByBadgeIdAndUserIdAndTier(b.getId(), userId, t)) {
                return t;
            }
        }
        return null;
    }

    // ---------- profils, fil, classement ----------

    @Transactional(readOnly = true)
    public ProfileDto profile(AuthUser me, UUID userId) {
        userLookup.require(userId);
        Map<UUID, Badge> all = badges.findAll().stream().collect(Collectors.toMap(Badge::getId, Function.identity()));
        List<Award> mine = awards.findByUserIdOrderByCreatedAtDesc(userId);
        long xp = mine.stream().mapToLong(Award::getPoints).sum();
        List<EarnedDto> earned = mine.stream().map(a -> toDto(all.get(a.getBadgeId()), a)).toList();
        Set<String> got = mine.stream().map(a -> a.getBadgeId() + "|" + a.getTier()).collect(Collectors.toSet());
        boolean self = me.id().equals(userId) || me.isAdmin();
        List<LockedDto> locked = new ArrayList<>();
        for (Badge b : all.values().stream().sorted(Comparator.comparing(Badge::getName)).toList()) {
            if (b.getRuleType() == RuleType.TIERED) {
                Tier next = List.of(Tier.BRONZE, Tier.SILVER, Tier.GOLD).stream().filter(t -> !got.contains(b.getId() + "|" + t)).findFirst().orElse(null);
                if (next != null) {
                    int target = next == Tier.BRONZE ? b.getTierBronze() : next == Tier.SILVER ? b.getTierSilver() : b.getTierGold();
                    locked.add(lockedDto(b, next, (int) events.countByUserIdAndType(userId, b.getEvent()), target, self));
                }
            } else if (!got.contains(b.getId() + "|" + Tier.NONE)) {
                Integer current = switch (b.getRuleType()) {
                    case COUNT -> (int) events.countByUserIdAndType(userId, b.getEvent());
                    case STREAK -> longestStreak(userId, b.getEvent());
                    default -> null;
                };
                locked.add(lockedDto(b, Tier.NONE, current, b.getThreshold(), self));
            }
        }
        return new ProfileDto(userId, userLookup.name(userId), level(xp), xp, (int) (xp % 100), earned, locked);
    }

    @Transactional(readOnly = true)
    public FeedPage feed(Instant cursor) {
        List<Award> page = awards.findByCreatedAtBeforeOrderByCreatedAtDesc(cursor == null ? clock.instant().plusSeconds(1) : cursor, PageRequest.of(0, FEED_PAGE));
        Map<UUID, Badge> all = badges.findAll().stream().collect(Collectors.toMap(Badge::getId, Function.identity()));
        Map<UUID, String> names = userLookup.namesOf(page, Award::getUserId);
        List<FeedItem> items = page.stream().map(a -> {
            Badge b = all.get(a.getBadgeId());
            return new FeedItem(a.getId(), a.getUserId(), names.get(a.getUserId()), b.getName(), b.getIcon(), b.getRarity(), a.getTier(), a.getCreatedAt());
        }).toList();
        return new FeedPage(items, page.size() < FEED_PAGE ? null : page.get(page.size() - 1).getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<RankDto> leaderboard() {
        List<Object[]> rows = awards.xpRanking(PageRequest.of(0, 50));
        List<UUID> ids = rows.stream().map(r -> (UUID) r[0]).toList();
        Map<UUID, String> names = userLookup.names(ids);
        List<RankDto> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            long xp = ((Number) rows.get(i)[1]).longValue();
            out.add(new RankDto(i + 1, ids.get(i), names.get(ids.get(i)), xp, level(xp)));
        }
        return out;
    }

    static int level(long xp) {
        return 1 + (int) (xp / 100);
    }

    // ---------- conversions ----------

    private BadgeDto toDto(Badge b, boolean hidden, long holders) {
        if (hidden) {
            return new BadgeDto(b.getId(), null, "Badge secret", "❓", "À découvrir…", b.getRarity(), true, null, null, null, null, null, null,
                    b.getRarity().points, holders, true);
        }
        return new BadgeDto(b.getId(), b.getCode(), b.getName(), b.getIcon(), b.getDescription(), b.getRarity(), b.isSecret(), b.getRuleType(),
                b.getEvent(), b.getThreshold(), b.getTierBronze(), b.getTierSilver(), b.getTierGold(), b.getRarity().points, holders, false);
    }

    private EarnedDto toDto(Badge b, Award a) {
        return new EarnedDto(b.getId(), b.getCode(), b.getName(), b.getIcon(), b.getRarity(), a.getTier(), a.getPoints(), a.getReason(), a.getCreatedAt());
    }

    private LockedDto lockedDto(Badge b, Tier next, Integer current, Integer target, boolean self) {
        boolean hidden = b.isSecret();
        return new LockedDto(b.getId(), hidden ? "Badge secret" : b.getName(), hidden ? "❓" : b.getIcon(), b.getRarity(),
                hidden ? "À découvrir…" : b.getDescription(), next, hidden || !self ? null : current, hidden ? null : target, hidden);
    }
}
