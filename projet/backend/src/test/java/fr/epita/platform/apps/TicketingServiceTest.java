package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.ticketing.dto.TicketingDtos.*;
import fr.epita.platform.apps.ticketing.entity.TicketOrderStatus;
import fr.epita.platform.apps.ticketing.service.TicketingService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TicketingServiceTest extends IntegrationTest {

    @Autowired
    TicketingService service;
    @Autowired
    AppRoles roles;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser org;
    AuthUser scanner;
    AuthUser buyer;
    Instant starts;

    @BeforeEach
    void setUp() {
        org = auth(user("Organisatrice"));
        scanner = auth(user("Contrôleur"));
        roles.grant(TicketingService.ROLES, Set.of(TicketingService.SCANNER), scanner.id(), TicketingService.SCANNER);
        buyer = auth(user("Acheteur"));
        starts = Instant.now().plus(10, ChronoUnit.DAYS);
    }

    private EventDto event(int capacity, int q1, int q2) {
        return service.create(org, new EventRequest("Concert", "Amphi", starts, capacity, starts.minus(1, ChronoUnit.HOURS),
                List.of(new RateInput(null, "Plein", new BigDecimal("15"), q1), new RateInput(null, "Étudiant", new BigDecimal("8"), q2))));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void quotasLimitsAndTotals() {
        assertThatThrownBy(() -> event(10, 8, 5)).isInstanceOf(ValidationException.class);
        EventDto e = event(10, 6, 4);
        var plein = e.rates().get(0).id();
        var etu = e.rates().get(1).id();
        assertCode(() -> service.order(buyer, e.id(), List.of(new OrderLine(plein, 4), new OrderLine(etu, 3))), "ORDER_LIMIT");
        OrderDto o = service.order(buyer, e.id(), List.of(new OrderLine(plein, 2), new OrderLine(etu, 3)));
        assertThat(o.total()).isEqualByComparingTo("54");
        assertThat(o.tickets()).hasSize(5).allSatisfy(t -> assertThat(t.code()).hasSize(45));
        assertCode(() -> service.order(buyer, e.id(), List.of(new OrderLine(etu, 2))), "SOLD_OUT");
        assertThat(service.get(buyer, e.id()).rates().get(1).remaining()).isEqualTo(1);
        assertCode(() -> service.update(org, e.id(), new EventRequest("Concert", "Amphi", starts, 10, starts.minus(1, ChronoUnit.HOURS),
                List.of(new RateInput(plein, "Plein", new BigDecimal("15"), 6), new RateInput(etu, "Étudiant", new BigDecimal("8"), 2)))), "QUOTA_BELOW_SOLD");
    }

    @Test
    void concurrentPurchasesNeverOversell() throws Exception {
        EventDto e = service.create(org, new EventRequest("Rare", "Box", starts, 5, starts.minus(1, ChronoUnit.HOURS),
                List.of(new RateInput(null, "Unique", BigDecimal.TEN, 5))));
        var rate = e.rates().get(0).id();
        List<AuthUser> buyers = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            buyers.add(auth(user("Acheteur " + i)));
        }
        var pool = Executors.newFixedThreadPool(4);
        List<Future<Boolean>> results = new ArrayList<>();
        for (AuthUser b : buyers) {
            Callable<Boolean> task = () -> { try { service.order(b, e.id(), List.of(new OrderLine(rate, 2))); return true; } catch (ApiException ex) { return false; } };
            results.add(pool.submit(task));
        }
        int ok = 0;
        for (Future<Boolean> f : results) {
            ok += f.get() ? 1 : 0;
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(2);
        assertThat(service.get(org, e.id()).sold()).isEqualTo(4);
    }

    @Test
    void scanningRules() {
        EventDto e = event(10, 5, 5);
        EventDto other = event(10, 5, 5);
        OrderDto o = service.order(buyer, e.id(), List.of(new OrderLine(e.rates().get(0).id(), 1)));
        String code = o.tickets().get(0).code();
        assertCode(() -> service.scan(buyer, e.id(), code), "FORBIDDEN");
        assertCode(() -> service.scan(scanner, e.id(), code.substring(0, 44) + (code.endsWith("0") ? "1" : "0")), "NOT_FOUND");
        assertCode(() -> service.scan(scanner, other.id(), code), "WRONG_EVENT");
        assertThat(service.scan(scanner, e.id(), code).holderName()).isEqualTo("Acheteur");
        assertCode(() -> service.scan(org, e.id(), code), "ALREADY_SCANNED");
        assertThat(service.dashboard(org, e.id()).entered()).isEqualTo(1);
        assertCode(() -> service.refund(buyer, o.id()), "TICKET_SCANNED");
    }

    @Test
    void refundsUntil48HoursBeforeAndSeatsComeBack() {
        EventDto e = event(10, 2, 0);
        var rate = e.rates().get(0).id();
        OrderDto o = service.order(buyer, e.id(), List.of(new OrderLine(rate, 2)));
        assertCode(() -> service.order(buyer, e.id(), List.of(new OrderLine(rate, 1))), "SOLD_OUT");
        assertThat(service.refund(buyer, o.id()).status()).isEqualTo(TicketOrderStatus.REFUNDED);
        assertThat(service.get(buyer, e.id()).rates().get(0).remaining()).isEqualTo(2);
        OrderDto again = service.order(buyer, e.id(), List.of(new OrderLine(rate, 1)));
        jdbc.update("update ticketing_events set starts_at = now() + interval '24 hours', sales_end_at = now() + interval '23 hours' where id = ?", e.id());
        assertCode(() -> service.refund(buyer, again.id()), "REFUND_CLOSED");
        jdbc.update("update ticketing_events set sales_end_at = now() - interval '1 minute' where id = ?", e.id());
        assertCode(() -> service.order(buyer, e.id(), List.of(new OrderLine(rate, 1))), "SALES_CLOSED");
    }
}
