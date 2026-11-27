package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.access.dto.AccessDtos.*;
import fr.epita.platform.apps.access.entity.AccessLevel;
import fr.epita.platform.apps.access.entity.AccessRequestStatus;
import fr.epita.platform.apps.access.entity.AuditAction;
import fr.epita.platform.apps.access.service.AccessService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import fr.epita.platform.user.dto.UserDtos.UpdateUserRequest;
import fr.epita.platform.user.entity.Role;
import fr.epita.platform.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessServiceTest extends IntegrationTest {

    @Autowired
    AccessService service;
    @Autowired
    ManagerRelations relations;
    @Autowired
    UserService userService;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser admin;
    AuthUser manager;
    AuthUser owner;
    AuthUser alice;
    ToolDto jira;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        manager = auth(user("Maya Manager"));
        owner = auth(user("Olivier Owner"));
        alice = auth(user("Alice"));
        relations.assign(AccessService.REPORTS, alice.id(), manager.id());
        jira = service.addTool(admin, new ToolRequest("Jira", owner.id(), List.of(AccessLevel.READ, AccessLevel.WRITE, AccessLevel.ADMIN)));
    }

    private AccessRequestDto ask(AuthUser who, AccessLevel level, int days) {
        return service.request(who, new AccessRequestBody(jira.id(), level, "Besoin projet", days));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void twoStepApprovalGrantsTheAccess() {
        AccessRequestDto r = ask(alice, AccessLevel.READ, 30);
        assertThat(r.status()).isEqualTo(AccessRequestStatus.PENDING_MANAGER);
        assertCode(() -> service.approve(owner, r.id()), "FORBIDDEN");
        assertCode(() -> service.approve(alice, r.id()), "SELF_APPROVAL");
        assertThat(service.approve(manager, r.id()).status()).isEqualTo(AccessRequestStatus.PENDING_OWNER);
        assertThat(service.myGrants(alice)).isEmpty();
        assertThat(service.toApprove(owner)).hasSize(1);
        assertThat(service.approve(owner, r.id()).status()).isEqualTo(AccessRequestStatus.GRANTED);
        assertThat(service.myGrants(alice)).singleElement().satisfies(g -> assertThat(g.level()).isEqualTo(AccessLevel.READ));
        assertCode(() -> ask(alice, AccessLevel.READ, 30), "ALREADY_GRANTED");
    }

    @Test
    void managerWhoOwnsTheToolApprovesOnceAndHigherLevelReplacesTheOldGrant() {
        relations.assign(AccessService.REPORTS, alice.id(), owner.id());
        service.approve(owner, ask(alice, AccessLevel.READ, 30).id());
        AccessRequestDto write = ask(alice, AccessLevel.WRITE, 60);
        assertThat(service.approve(owner, write.id()).status()).isEqualTo(AccessRequestStatus.GRANTED);
        assertThat(service.myGrants(alice)).singleElement().satisfies(g -> assertThat(g.level()).isEqualTo(AccessLevel.WRITE));
        assertThat(service.audit(admin, alice.id(), null)).extracting(AuditDto::action).contains(AuditAction.REPLACED, AuditAction.GRANTED);
    }

    @Test
    void durationRulesExpiryAndReview() {
        assertThatThrownBy(() -> ask(alice, AccessLevel.ADMIN, 120)).isInstanceOf(ValidationException.class);
        AccessRequestDto r = ask(alice, AccessLevel.ADMIN, 90);
        service.approve(manager, r.id());
        service.approve(owner, r.id());
        GrantDto g = service.myGrants(alice).get(0);
        jdbc.update("update access_grants set last_confirmed_at = now() - interval '200 days' where id = ?", g.id());
        assertThat(service.toolGrants(owner, jira.id()).get(0).reviewDue()).isTrue();
        assertThat(service.confirm(owner, g.id()).reviewDue()).isFalse();
        jdbc.update("update access_grants set expires_at = now() - interval '1 minute' where id = ?", g.id());
        assertThat(service.myGrants(alice)).isEmpty();
        assertThat(service.audit(admin, null, jira.id())).extracting(AuditDto::action).contains(AuditAction.EXPIRED, AuditAction.CONFIRMED);
        AccessRequestDto other = ask(alice, AccessLevel.READ, 10);
        assertThat(service.reject(manager, other.id(), "Lecture inutile").status()).isEqualTo(AccessRequestStatus.REJECTED);
        assertCode(() -> service.reject(manager, other.id(), "encore"), "INVALID_STATUS_TRANSITION");
    }

    @Test
    void disablingAUserRevokesAllTheirAccesses() {
        AccessRequestDto r = ask(alice, AccessLevel.WRITE, 30);
        service.approve(manager, r.id());
        service.approve(owner, r.id());
        AccessRequestDto pending = service.request(alice, new AccessRequestBody(
                service.addTool(admin, new ToolRequest("Grafana", owner.id(), List.of(AccessLevel.READ))).id(), AccessLevel.READ, "Supervision", 30));
        userService.update(alice.id(), new UpdateUserRequest(alice.email(), "Alice", Role.USER, false));
        assertThat(service.toolGrants(owner, jira.id())).isEmpty();
        assertThat(service.mine(alice).stream().filter(x -> x.id().equals(pending.id())).findFirst().orElseThrow().status()).isEqualTo(AccessRequestStatus.REJECTED);
        assertThat(service.audit(admin, alice.id(), jira.id()).get(0).action()).isEqualTo(AuditAction.REVOKED);
    }
}
