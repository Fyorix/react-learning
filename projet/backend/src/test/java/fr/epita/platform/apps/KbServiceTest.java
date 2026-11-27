package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.kb.dto.KbDtos.*;
import fr.epita.platform.apps.kb.entity.VersionStatus;
import fr.epita.platform.apps.kb.service.KbService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KbServiceTest extends IntegrationTest {

    @Autowired
    KbService service;
    @Autowired
    AppRoles roles;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser author;
    AuthUser reviewer;
    AuthUser reader;

    @BeforeEach
    void setUp() {
        author = auth(user("Alice Autrice"));
        reviewer = auth(user("Rémi Relecteur"));
        reader = auth(user("Lucie Lectrice"));
        roles.grant(KbService.ROLES, Set.of(KbService.REVIEWER), reviewer.id(), KbService.REVIEWER);
        roles.grant(KbService.ROLES, Set.of(KbService.REVIEWER), author.id(), KbService.REVIEWER);
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    private void publishVpn() {
        service.create(author, new CreateRequest("vpn", "Configurer le VPN", "Informatique", List.of("VPN", "réseau"), "Installer le client"));
        service.submit(author, "vpn");
        service.publish(reviewer, "vpn", 1);
    }

    @Test
    void publicationNeedsAnotherReviewer() {
        service.create(author, new CreateRequest("vpn", "Configurer le VPN", "Informatique", List.of(), "Installer le client"));
        assertCode(() -> service.read(reader, "vpn"), "NOT_FOUND");
        assertCode(() -> service.create(reader, new CreateRequest("vpn", "x", "y", List.of(), "z")), "SLUG_EXISTS");
        service.submit(author, "vpn");
        assertCode(() -> service.publish(author, "vpn", 1), "SELF_REVIEW");
        assertCode(() -> service.publish(reader, "vpn", 1), "FORBIDDEN");
        service.publish(reviewer, "vpn", 1);
        assertThat(service.read(reader, "vpn").version()).isEqualTo(1);
    }

    @Test
    void publishedVersionStaysVisibleDuringReviewOfANewOne() {
        publishVpn();
        VersionDto v2 = service.saveDraft(reader, "vpn", new DraftRequest("Configurer le VPN (2026)", "Informatique", List.of("vpn"), "Nouveau client"));
        assertThat(v2.number()).isEqualTo(2);
        service.submit(reader, "vpn");
        assertCode(() -> service.saveDraft(author, "vpn", new DraftRequest("x", "y", List.of(), "z")), "REVIEW_IN_PROGRESS");
        ArticleDto visible = service.read(author, "vpn");
        assertThat(visible.version()).isEqualTo(1);
        assertThat(visible.pendingVersion()).isEqualTo(2);
        service.requestChanges(reviewer, "vpn", 2, "Préciser la version");
        assertThat(service.mine(reader).get(0).latestStatus()).isEqualTo(VersionStatus.CHANGES_REQUESTED);
        service.submit(reader, "vpn");
        service.publish(author, "vpn", 2);
        assertThat(service.read(reader, "vpn").title()).isEqualTo("Configurer le VPN (2026)");
        // historique : le lecteur voit les versions publiées et la sienne
        assertThat(service.history(reader, "vpn")).hasSize(2);
    }

    @Test
    void restoreCreatesANewVersionToReview() {
        publishVpn();
        service.saveDraft(author, "vpn", new DraftRequest("VPN v2", "Informatique", List.of(), "v2"));
        service.submit(author, "vpn");
        service.publish(reviewer, "vpn", 2);
        VersionDto restored = service.restore(reader, "vpn", 1);
        assertThat(restored.number()).isEqualTo(3);
        assertThat(restored.status()).isEqualTo(VersionStatus.IN_REVIEW);
        assertThat(restored.restoredFrom()).isEqualTo(1);
        assertThat(restored.content()).isEqualTo("Installer le client");
        assertThat(service.reviews(reviewer)).hasSize(1);
    }

    @Test
    void searchFeedbackStaleAndStats() {
        publishVpn();
        assertThat(service.search("client vpn", null, null, null)).hasSize(1);
        assertThat(service.search("imprimante", null, null, null)).isEmpty();
        assertThat(service.search(null, null, "réseau", null)).hasSize(1);
        service.feedback(reader, "vpn", true);
        ArticleDto changed = service.feedback(reader, "vpn", false);
        assertThat(changed.helpful()).isZero();
        assertThat(changed.notHelpful()).isEqualTo(1);
        jdbc.update("update kb_articles set published_at = now() - interval '400 days' where slug = 'vpn'");
        assertThat(service.search(null, null, null, true)).hasSize(1);
        assertThat(service.stats().stale()).hasSize(1);
    }
}
