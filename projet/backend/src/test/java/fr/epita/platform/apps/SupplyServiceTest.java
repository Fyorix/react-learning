package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.supply.dto.SupplyDtos.*;
import fr.epita.platform.apps.supply.entity.RequestStatus;
import fr.epita.platform.apps.supply.service.SupplyService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.relation.ManagerRelations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupplyServiceTest extends IntegrationTest {

    @Autowired
    SupplyService service;
    @Autowired
    ManagerRelations relations;

    AuthUser admin;
    AuthUser alice;
    AuthUser bob;
    ItemDto pens;
    ItemDto paper;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        alice = auth(user("Alice"));
        bob = auth(user("Bob"));
        pens = service.create(admin, new ItemRequest("Stylo", "Écriture", "pièce", 10, 4, 5, true));
        paper = service.create(admin, new ItemRequest("Ramette A4", "Papier", "ramette", 3, 2, null, true));
    }

    private RequestDto ask(AuthUser who, ItemDto item, int q) {
        return service.request(who, new SupplyRequestBody(List.of(new LineRequest(item.id(), q))));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void approvalReservesAndDeliveryDecrementsStock() {
        RequestDto r = ask(alice, paper, 2);
        service.approve(admin, r.id());
        ItemDto after = service.catalog(admin, null, "ramette").get(0);
        assertThat(after.stock()).isEqualTo(3);
        assertThat(after.reserved()).isEqualTo(2);
        assertThat(after.available()).isEqualTo(1);
        assertCode(() -> ask(bob, paper, 2), "INSUFFICIENT_STOCK");
        service.deliver(admin, r.id());
        ItemDto delivered = service.catalog(admin, null, "ramette").get(0);
        assertThat(delivered.stock()).isEqualTo(1);
        assertThat(delivered.reserved()).isZero();
        assertThat(service.alerts(admin)).extracting(ItemDto::name).contains("Ramette A4");
        assertCode(() -> service.cancel(alice, r.id()), "REQUEST_DELIVERED");
    }

    @Test
    void stockIsRecheckedAtApproval() {
        RequestDto a = ask(alice, paper, 2);
        RequestDto b = ask(bob, paper, 2);
        service.approve(admin, a.id());
        assertCode(() -> service.approve(admin, b.id()), "INSUFFICIENT_STOCK");
        service.restock(admin, paper.id(), 5);
        assertThat(service.approve(admin, b.id()).status()).isEqualTo(RequestStatus.APPROVED);
    }

    @Test
    void monthlyQuotaPerPerson() {
        ask(alice, pens, 3);
        assertCode(() -> ask(alice, pens, 3), "QUOTA_EXCEEDED");
        ask(alice, pens, 2);
        assertThat(service.catalog(alice, null, "stylo").get(0).myRemainingQuota()).isZero();
        assertThat(ask(bob, pens, 5).status()).isEqualTo(RequestStatus.PENDING);
    }

    @Test
    void cancelReleasesReservationAndRejectNeedsPendingOrApproved() {
        RequestDto r = ask(alice, paper, 3);
        service.approve(admin, r.id());
        service.cancel(alice, r.id());
        assertThat(service.catalog(admin, null, "ramette").get(0).available()).isEqualTo(3);
        assertCode(() -> service.reject(admin, r.id(), "trop tard"), "INVALID_STATUS_TRANSITION");
        assertCode(() -> service.cancel(bob, r.id()), "NOT_FOUND");
    }

    @Test
    void consumptionByItemAndTeam() {
        relations.assign(SupplyService.REPORTS, alice.id(), bob.id());
        RequestDto r = ask(alice, pens, 2);
        service.approve(admin, r.id());
        service.deliver(admin, r.id());
        ConsumptionDto c = service.consumption(admin, null, null);
        assertThat(c.byItem()).singleElement().satisfies(x -> assertThat(x.quantity()).isEqualTo(2));
        assertThat(c.byTeam().get(0).label()).isEqualTo("Équipe de Bob");
    }
}
