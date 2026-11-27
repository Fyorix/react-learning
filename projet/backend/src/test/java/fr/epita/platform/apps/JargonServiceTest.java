package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.jargon.dto.JargonDtos.*;
import fr.epita.platform.apps.jargon.entity.ContributionStatus;
import fr.epita.platform.apps.jargon.service.JargonService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JargonServiceTest extends IntegrationTest {

    @Autowired
    JargonService service;
    @Autowired
    AppRoles roles;

    AuthUser admin;
    AuthUser mod;
    AuthUser alice;
    AuthUser bob;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        mod = auth(user("Modérateur"));
        roles.grant(JargonService.ROLES, Set.of(JargonService.MODERATOR), mod.id(), JargonService.MODERATOR);
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
    }

    private ContributionDto publish(AuthUser author, String term, String expansion) {
        ContributionDto c = service.proposeTerm(author, new TermRequest(term, expansion, "DevOps", "Définition de " + term, null, List.of()));
        return service.approve(mod, c.id());
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void termsAreUniqueIgnoringCaseAndAccentsAndModerated() {
        ContributionDto c = service.proposeTerm(alice, new TermRequest("Réseau", null, null, "Ensemble de machines", null, List.of()));
        assertThat(c.status()).isEqualTo(ContributionStatus.PENDING);
        assertCode(() -> service.proposeTerm(bob, new TermRequest("RESEAU", null, null, "x", null, List.of())), "TERM_EXISTS");
        assertThat(service.suggest("res", 10)).isEmpty();
        assertCode(() -> service.approve(alice, c.id()), "FORBIDDEN");
        assertThat(service.approve(mod, c.id()).status()).isEqualTo(ContributionStatus.PUBLISHED);
        assertThat(service.suggest("RÉS", 10)).extracting(Suggestion::term).containsExactly("Réseau");
        ContributionDto own = service.proposeTerm(mod, new TermRequest("SLA", null, null, "Engagement de service", null, List.of()));
        assertCode(() -> service.approve(mod, own.id()), "SELF_MODERATION");
        ContributionDto rejected = service.reject(admin, own.id(), "Doublon");
        assertThat(rejected.status()).isEqualTo(ContributionStatus.REJECTED);
        assertThat(service.mine(mod)).singleElement().satisfies(x -> assertThat(x.reason()).isEqualTo("Doublon"));
    }

    @Test
    void suggestionsAreLimitedAndMissingSearchesCounted() {
        for (int i = 0; i < 12; i++) {
            publish(alice, "API%02d".formatted(i), null);
        }
        assertThat(service.suggest("api", 50)).hasSize(10);
        assertThat(service.suggest("a%", 10)).isEmpty();
        service.suggest("kpi", 10);
        service.suggest("KPI", 10);
        assertThat(service.missing(admin)).extracting(MissingDto::hits).contains(2);
        assertThat(service.list(null, "a", null, 0, 5).totalElements()).isEqualTo(12);
    }

    @Test
    void definitionsAreSortedByHelpfulVotes() {
        ContributionDto term = publish(alice, "MEP", "Mise en production");
        ContributionDto second = service.proposeDefinition(bob, term.termId(), new DefinitionRequest("Release côté mobile", "MEP jeudi"));
        assertThat(service.get(alice, term.termId()).definitions()).hasSize(1);
        service.approve(mod, second.id());
        assertCode(() -> service.vote(bob, second.id()), "OWN_DEFINITION");
        service.vote(alice, second.id());
        assertThat(service.vote(mod, second.id())).isEqualTo(2);
        service.vote(mod, second.id());
        TermDto dto = service.get(alice, term.termId());
        assertThat(dto.definitions().get(0).id()).isEqualTo(second.id());
        assertThat(dto.definitions().get(0).helpful()).isEqualTo(2);
        assertThat(service.unvote(alice, second.id())).isEqualTo(1);
    }

    @Test
    void relatedTermsMustBePublished() {
        ContributionDto a = publish(alice, "CI", "Intégration continue");
        ContributionDto pending = service.proposeTerm(alice, new TermRequest("CD", null, null, "Déploiement continu", null, List.of()));
        assertThatThrownBy(() -> service.proposeTerm(bob, new TermRequest("CI/CD", null, null, "x", null, List.of(pending.termId()))))
                .isInstanceOf(fr.epita.platform.common.ValidationException.class);
        ContributionDto b = service.approve(mod, service.proposeTerm(bob, new TermRequest("CI/CD", null, null, "Chaîne complète", null, List.of(a.termId()))).id());
        assertThat(service.get(alice, b.termId()).related()).extracting(Suggestion::term).containsExactly("CI");
    }
}
