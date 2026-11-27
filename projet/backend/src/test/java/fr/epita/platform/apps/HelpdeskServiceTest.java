package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.CommentRequest;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.TicketDto;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.TicketRequest;
import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.Category;
import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.Priority;
import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.TicketStatus;
import fr.epita.platform.apps.helpdesk.service.HelpdeskService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HelpdeskServiceTest extends IntegrationTest {

    @Autowired
    HelpdeskService service;
    @Autowired
    AppRoles roles;

    AuthUser tech;
    AuthUser otherTech;
    AuthUser alice;
    AuthUser bob;

    @BeforeEach
    void setUp() {
        admin("Admin");
        tech = auth(user("Tom Tech"));
        otherTech = auth(user("Tina Tech"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        roles.grant(HelpdeskService.ROLES, Set.of(HelpdeskService.TECHNICIAN), tech.id(), HelpdeskService.TECHNICIAN);
        roles.grant(HelpdeskService.ROLES, Set.of(HelpdeskService.TECHNICIAN), otherTech.id(), HelpdeskService.TECHNICIAN);
    }

    private TicketDto open(Priority p) {
        return service.open(alice, new TicketRequest("Imprimante", "Hors ligne", Category.HARDWARE, p));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void dueDateDependsOnPriority() {
        TicketDto t = open(Priority.CRITICAL);
        assertThat(Duration.between(t.createdAt(), t.dueAt()).toHours()).isEqualTo(4);
        TicketDto n = open(Priority.NORMAL);
        assertThat(Duration.between(n.createdAt(), n.dueAt()).toDays()).isEqualTo(3);
    }

    @Test
    void requestersSeeOnlyTheirTicketsAndNoInternalComments() {
        TicketDto t = open(Priority.HIGH);
        service.comment(tech, t.id(), new CommentRequest("Note interne", true));
        service.comment(tech, t.id(), new CommentRequest("Je regarde", false));
        assertThat(service.get(alice, t.id()).comments()).extracting("content").containsExactly("Je regarde");
        assertThat(service.get(tech, t.id()).comments()).hasSize(2);
        assertCode(() -> service.get(bob, t.id()), "NOT_FOUND");
        assertCode(() -> service.comment(alice, t.id(), new CommentRequest("x", true)), "FORBIDDEN");
        assertThat(service.list(bob, null, null, null, null, 0, 20).content()).isEmpty();
    }

    @Test
    void onlyTheAssignedTechnicianChangesTheStatusAlongTheWorkflow() {
        TicketDto t = open(Priority.NORMAL);
        assertCode(() -> service.assign(tech, t.id(), otherTech.id()), "FORBIDDEN");
        service.assign(tech, t.id(), tech.id());
        assertCode(() -> service.changeStatus(otherTech, t.id(), TicketStatus.IN_PROGRESS, null), "FORBIDDEN");
        assertCode(() -> service.changeStatus(tech, t.id(), TicketStatus.RESOLVED, null), "INVALID_STATUS_TRANSITION");
        service.changeStatus(tech, t.id(), TicketStatus.IN_PROGRESS, null);
        TicketDto resolved = service.changeStatus(tech, t.id(), TicketStatus.RESOLVED, "Toner remplacé");
        assertThat(resolved.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(resolved.comments()).extracting("content").contains("Toner remplacé");
    }

    @Test
    void requesterCanReopenWithinSevenDaysOnly() {
        TicketDto t = open(Priority.LOW);
        service.assign(tech, t.id(), tech.id());
        service.changeStatus(tech, t.id(), TicketStatus.IN_PROGRESS, null);
        service.changeStatus(tech, t.id(), TicketStatus.RESOLVED, null);
        assertCode(() -> service.reopen(bob, t.id(), "x"), "FORBIDDEN");
        assertThat(service.reopen(alice, t.id(), "Encore en panne").status()).isEqualTo(TicketStatus.IN_PROGRESS);

        service.changeStatus(tech, t.id(), TicketStatus.RESOLVED, null);
        jdbc.update("update helpdesk_tickets set resolved_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(8))), t.id());
        assertCode(() -> service.reopen(alice, t.id(), "Toujours"), "REOPEN_EXPIRED");
        assertThat(service.get(alice, t.id()).status()).isEqualTo(TicketStatus.CLOSED);
    }

    @Test
    void overdueFilterAndStats() {
        TicketDto t = open(Priority.CRITICAL);
        jdbc.update("update helpdesk_tickets set due_at = ? where id = ?", java.sql.Timestamp.from(Instant.now().minusSeconds(60)), t.id());
        open(Priority.LOW);
        assertThat(service.list(tech, null, null, null, true, 0, 20).content()).extracting(TicketDto::id).containsExactly(t.id());
        assertThat(service.stats(tech).overdue()).isEqualTo(1);
        assertCode(() -> service.stats(alice), "FORBIDDEN");
    }
}
