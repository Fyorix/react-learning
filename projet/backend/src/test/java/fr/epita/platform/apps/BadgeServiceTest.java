package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.badge.dto.BadgeDtos.*;
import fr.epita.platform.apps.badge.entity.Rarity;
import fr.epita.platform.apps.badge.entity.RuleType;
import fr.epita.platform.apps.badge.entity.Tier;
import fr.epita.platform.apps.badge.service.BadgeService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BadgeServiceTest extends IntegrationTest {

    @Autowired
    BadgeService service;

    AuthUser admin;
    AuthUser alice;
    AuthUser bob;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
    }

    private BadgeDto badge(String code, Rarity rarity, RuleType type, String event, Integer threshold, boolean secret, Integer... tiers) {
        return service.create(admin, new BadgeRequest(code, "Badge " + code, "🏅", "desc", rarity, secret, type, event, threshold,
                tiers.length > 0 ? tiers[0] : null, tiers.length > 1 ? tiers[1] : null, tiers.length > 2 ? tiers[2] : null));
    }

    private List<EarnedDto> event(AuthUser who, String type) {
        return service.record(who, new EventRequest(type, null, null));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void countBadgeIsAwardedOnceAtTheThresholdAndGivesXp() {
        badge("GIVER", Rarity.RARE, RuleType.COUNT, "KUDO_SENT", 3, false);
        assertThat(event(alice, "KUDO_SENT")).isEmpty();
        assertThat(event(alice, "KUDO_SENT")).isEmpty();
        assertThat(event(alice, "KUDO_SENT")).extracting(EarnedDto::code).containsExactly("GIVER");
        assertThat(event(alice, "KUDO_SENT")).isEmpty();
        ProfileDto p = service.profile(alice, alice.id());
        assertThat(p.xp()).isEqualTo(25);
        assertThat(p.level()).isEqualTo(1);
        assertThat(service.leaderboard().get(0).userId()).isEqualTo(alice.id());
    }

    @Test
    void tieredBadgesAwardEachTierAndProgressIsShown() {
        BadgeDto b = badge("WRITER", Rarity.EPIC, RuleType.TIERED, "ARTICLE", null, false, 1, 2, 3);
        assertThat(event(alice, "ARTICLE")).extracting(EarnedDto::tier).containsExactly(Tier.BRONZE);
        event(alice, "ARTICLE");
        ProfileDto mid = service.profile(alice, alice.id());
        LockedDto next = mid.locked().stream().filter(l -> l.badgeId().equals(b.id())).findFirst().orElseThrow();
        assertThat(next.nextTier()).isEqualTo(Tier.GOLD);
        assertThat(next.current()).isEqualTo(2);
        assertThat(next.target()).isEqualTo(3);
        event(alice, "ARTICLE");
        ProfileDto p = service.profile(alice, alice.id());
        assertThat(p.xp()).isEqualTo(150);
        assertThat(p.level()).isEqualTo(2);
        // la progression chiffrée n'est pas visible d'un collègue
        assertThat(service.profile(bob, alice.id()).locked()).allMatch(l -> l.current() == null);
    }

    @Test
    void streakCountsConsecutiveDaysAndBadgesAreNeverRemoved() {
        BadgeDto b = badge("ASSIDU", Rarity.COMMON, RuleType.STREAK, "DAILY_LOGIN", 3, false);
        Instant now = Instant.now();
        service.record(alice, new EventRequest("DAILY_LOGIN", now.minus(3, ChronoUnit.DAYS), null));
        service.record(alice, new EventRequest("DAILY_LOGIN", now.minus(1, ChronoUnit.DAYS), null));
        assertThat(service.record(alice, new EventRequest("DAILY_LOGIN", now, null))).isEmpty();
        assertThat(service.record(alice, new EventRequest("DAILY_LOGIN", now.minus(2, ChronoUnit.DAYS), null))).hasSize(1);
        // la règle devient plus dure : le badge reste acquis
        service.update(admin, b.id(), new BadgeRequest("ASSIDU", "Assidu", "📅", "desc", Rarity.COMMON, false, RuleType.STREAK, "DAILY_LOGIN", 30, null, null, null));
        assertThat(service.profile(alice, alice.id()).earned()).extracting(EarnedDto::code).contains("ASSIDU");
        assertThatThrownBy(() -> service.record(alice, new EventRequest("DAILY_LOGIN", now.plus(1, ChronoUnit.DAYS), null))).isInstanceOf(RuntimeException.class);
    }

    @Test
    void secretBadgesAreMaskedAndManualAwardNeedsAdmin() {
        BadgeDto secret = badge("NIGHT_OWL", Rarity.LEGENDARY, RuleType.COUNT, "LATE_COMMIT", 1, true);
        BadgeDto special = badge("SPECIAL", Rarity.EPIC, RuleType.MANUAL, null, null, false);
        BadgeDto seen = service.catalog(bob).stream().filter(x -> x.id().equals(secret.id())).findFirst().orElseThrow();
        assertThat(seen.hidden()).isTrue();
        assertThat(seen.name()).isEqualTo("Badge secret");
        assertThat(seen.event()).isNull();
        event(bob, "LATE_COMMIT");
        assertThat(service.catalog(bob).stream().filter(x -> x.id().equals(secret.id())).findFirst().orElseThrow().hidden()).isFalse();
        assertCode(() -> service.awardManually(alice, special.id(), new AwardRequest(bob.id(), "Bravo")), "FORBIDDEN");
        service.awardManually(admin, special.id(), new AwardRequest(bob.id(), "Organisation du séminaire"));
        assertCode(() -> service.awardManually(admin, special.id(), new AwardRequest(bob.id(), "encore")), "ALREADY_AWARDED");
        assertCode(() -> service.record(alice, new EventRequest("X_EVENT", null, bob.id())), "FORBIDDEN");
        assertThat(service.feed(null).items()).hasSize(2);
    }
}
