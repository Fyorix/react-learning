package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.grouporder.dto.GroupOrderDtos.*;
import fr.epita.platform.apps.grouporder.entity.OrderStatus;
import fr.epita.platform.apps.grouporder.service.GroupOrderService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroupOrderServiceTest extends IntegrationTest {

    @Autowired
    GroupOrderService service;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser admin;
    AuthUser orga;
    AuthUser alice;
    AuthUser bob;
    SupplierDto pizzeria;
    ItemDto margherita;
    ItemDto regina;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        orga = auth(user("Olivia Orga"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        SupplierDto s = service.createSupplier(admin, new SupplierRequest("Pizzeria " + System.nanoTime(), new BigDecimal("20.00"), new BigDecimal("5.00")));
        pizzeria = service.replaceMenu(admin, s.id(), new MenuRequest(List.of(
                new MenuItemRequest(null, "Margherita", new BigDecimal("9.00"), true, List.of(new OptionRequest(null, "Burrata", new BigDecimal("2.50")))),
                new MenuItemRequest(null, "Regina", new BigDecimal("11.00"), true, List.of()),
                new MenuItemRequest(null, "Calzone", new BigDecimal("12.00"), false, List.of()))));
        margherita = pizzeria.items().get(0);
        regina = pizzeria.items().get(1);
    }

    private OrderDto open() {
        return service.open(orga, new OrderRequest(pizzeria.id(), Instant.now().plus(2, ChronoUnit.HOURS), null));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void feeIsSplitToTheCentWithRemainderToOrganizer() {
        OrderDto o = open();
        service.replaceMyLines(alice, o.id(), new LinesRequest(List.of(new LineRequest(margherita.id(), List.of(margherita.options().get(0).id()), 1))));
        service.replaceMyLines(bob, o.id(), new LinesRequest(List.of(new LineRequest(regina.id(), null, 1))));
        OrderDto d = service.replaceMyLines(orga, o.id(), new LinesRequest(List.of(new LineRequest(margherita.id(), null, 1))));
        // 5,00 / 3 = 1,66 chacun, reste 0,02 pour l'organisatrice
        assertThat(d.persons()).extracting(PersonDto::feeShare).containsExactlyInAnyOrder(
                new BigDecimal("1.66"), new BigDecimal("1.66"), new BigDecimal("1.68"));
        PersonDto a = d.persons().stream().filter(p -> p.userId().equals(alice.id())).findFirst().orElseThrow();
        assertThat(a.lines().get(0).label()).isEqualTo("Margherita (Burrata)");
        assertThat(a.total()).isEqualByComparingTo("13.16");
        assertThat(d.total()).isEqualByComparingTo("36.50");
        assertThat(d.persons().stream().map(PersonDto::total).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo(d.total());
    }

    @Test
    void organizerWithoutLinesStillAbsorbsTheRemainder() {
        OrderDto o = open();
        service.replaceMyLines(alice, o.id(), new LinesRequest(List.of(new LineRequest(regina.id(), null, 1))));
        OrderDto d = service.replaceMyLines(bob, o.id(), new LinesRequest(List.of(new LineRequest(regina.id(), null, 1))));
        assertThat(d.persons()).hasSize(2);
        AuthUser c = auth(user("Chloé"));
        d = service.replaceMyLines(c, o.id(), new LinesRequest(List.of(new LineRequest(regina.id(), null, 1))));
        assertThat(d.persons()).hasSize(4);
        assertThat(d.persons().stream().map(PersonDto::total).reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("38.00");
    }

    @Test
    void unavailableItemsAndMinimumAndDeadline() {
        OrderDto o = open();
        ItemDto calzone = pizzeria.items().get(2);
        assertCode(() -> service.replaceMyLines(alice, o.id(), new LinesRequest(List.of(new LineRequest(calzone.id(), null, 1)))), "ITEM_UNAVAILABLE");
        service.replaceMyLines(alice, o.id(), new LinesRequest(List.of(new LineRequest(regina.id(), null, 1))));
        assertCode(() -> service.close(orga, o.id()), "MINIMUM_NOT_REACHED");
        assertCode(() -> service.close(alice, o.id()), "FORBIDDEN");
        jdbc.update("update grouporder_orders set deadline = now() - interval '1 minute' where id = ?", o.id());
        assertCode(() -> service.replaceMyLines(bob, o.id(), new LinesRequest(List.of(new LineRequest(regina.id(), null, 2)))), "ORDER_CLOSED");
    }

    @Test
    void closeThenPaymentsThenDelivered() {
        OrderDto o = open();
        service.replaceMyLines(alice, o.id(), new LinesRequest(List.of(new LineRequest(regina.id(), null, 2))));
        assertCode(() -> service.setPaid(orga, o.id(), alice.id(), true), "INVALID_STATUS_TRANSITION");
        OrderDto closed = service.close(orga, o.id());
        assertThat(closed.status()).isEqualTo(OrderStatus.CLOSED);
        assertThat(closed.persons()).allMatch(p -> p.userId().equals(orga.id()) ? p.paid() : !p.paid());
        assertCode(() -> service.replaceMyLines(bob, o.id(), new LinesRequest(List.of(new LineRequest(regina.id(), null, 1)))), "ORDER_CLOSED");
        OrderDto paid = service.setPaid(orga, o.id(), alice.id(), true);
        assertThat(paid.persons().stream().filter(p -> p.userId().equals(alice.id())).findFirst().orElseThrow().paid()).isTrue();
        assertThat(service.summary(o.id()).rows()).singleElement().satisfies(r -> assertThat(r.quantity()).isEqualTo(2));
        assertThat(service.delivered(orga, o.id()).status()).isEqualTo(OrderStatus.DELIVERED);
        assertCode(() -> service.cancel(orga, o.id()), "INVALID_STATUS_TRANSITION");
    }

    @Test
    void menuReplacementArchivesMissingItemsButKeepsFrozenPrices() {
        OrderDto o = open();
        service.replaceMyLines(alice, o.id(), new LinesRequest(List.of(new LineRequest(regina.id(), null, 1))));
        SupplierDto after = service.replaceMenu(admin, pizzeria.id(), new MenuRequest(List.of(
                new MenuItemRequest(margherita.id(), "Margherita", new BigDecimal("10.00"), true, List.of()))));
        assertThat(after.items()).extracting(ItemDto::name).containsExactly("Margherita");
        OrderDto d = service.get(alice, o.id());
        assertThat(d.mine().get(0).unitPrice()).isEqualByComparingTo("11.00");
        assertCode(() -> service.replaceMyLines(alice, o.id(), new LinesRequest(List.of(new LineRequest(regina.id(), null, 1)))), "ITEM_UNAVAILABLE");
    }
}
