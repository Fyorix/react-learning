package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.invoice.dto.InvoiceDtos.*;
import fr.epita.platform.apps.invoice.entity.InvoiceStatus;
import fr.epita.platform.apps.invoice.entity.PaymentMethod;
import fr.epita.platform.apps.invoice.service.InvoiceService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.relation.AppRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceServiceTest extends IntegrationTest {

    @Autowired
    InvoiceService service;
    @Autowired
    AppRoles roles;
    @Autowired
    JdbcTemplate jdbc;

    AuthUser accountant;
    AuthUser client;
    AuthUser other;

    @BeforeEach
    void setUp() {
        accountant = auth(user("Claire Comptable"));
        roles.grant(InvoiceService.ROLES, Set.of(InvoiceService.ACCOUNTANT), accountant.id(), InvoiceService.ACCOUNTANT);
        client = auth(user("Client SA"));
        other = auth(user("Autre client"));
    }

    private InvoiceDto draft() {
        // 2 × 100 € HT à 20 % + 1 × 50 € à 5,5 % → HT 250, TVA 40 + 2,75 = 42,75, TTC 292,75
        return service.create(accountant, new InvoiceRequest(client.id(), List.of(
                new LineRequest("Formation", new BigDecimal("2"), new BigDecimal("100.00"), new BigDecimal("20")),
                new LineRequest("Livre", new BigDecimal("1"), new BigDecimal("50.00"), new BigDecimal("5.5"))), LocalDate.now().plusDays(30)));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void totalsNumberingAndImmutability() {
        InvoiceDto d = draft();
        assertThat(d.totalTtc()).isEqualByComparingTo("292.75");
        assertThat(d.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertCode(() -> service.get(client, d.id()), "NOT_FOUND");
        InvoiceDto issued = service.issue(accountant, d.id());
        int year = LocalDate.now().getYear();
        assertThat(issued.number()).isEqualTo("FAC-" + year + "-0001");
        assertThat(service.issue(accountant, draft().id()).number()).isEqualTo("FAC-" + year + "-0002");
        assertCode(() -> service.issue(accountant, d.id()), "INVOICE_ISSUED");
        assertCode(() -> service.update(accountant, d.id(), new InvoiceRequest(client.id(), List.of(
                new LineRequest("x", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO)), LocalDate.now().plusDays(1))), "INVOICE_ISSUED");
        assertThat(service.get(client, d.id()).number()).isEqualTo(issued.number());
        assertCode(() -> service.get(other, d.id()), "NOT_FOUND");
        assertCode(() -> service.create(client, new InvoiceRequest(client.id(), List.of(), LocalDate.now())), "FORBIDDEN");
    }

    @Test
    void paymentsCreditsAndComputedStatuses() {
        InvoiceDto i = service.issue(accountant, draft().id());
        InvoiceDto partial = service.pay(accountant, i.id(), new PaymentRequest(LocalDate.now(), new BigDecimal("100.00"), PaymentMethod.TRANSFER));
        assertThat(partial.status()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
        assertThat(partial.remaining()).isEqualByComparingTo("192.75");
        assertCode(() -> service.credit(accountant, i.id(), new CreditRequest(new BigDecimal("200.00"), "trop")), "CREDIT_TOO_HIGH");
        InvoiceDto credited = service.credit(accountant, i.id(), new CreditRequest(new BigDecimal("42.75"), "Remise"));
        assertThat(credited.creditNotes().get(0).number()).isEqualTo("AVO-" + LocalDate.now().getYear() + "-0001");
        assertCode(() -> service.pay(accountant, i.id(), new PaymentRequest(LocalDate.now(), new BigDecimal("150.01"), PaymentMethod.CARD)), "OVERPAYMENT");
        assertThat(service.pay(accountant, i.id(), new PaymentRequest(LocalDate.now(), new BigDecimal("150.00"), PaymentMethod.CARD)).status())
                .isEqualTo(InvoiceStatus.PAID);
        InvoiceDto other = service.issue(accountant, draft().id());
        assertThat(service.credit(accountant, other.id(), new CreditRequest(new BigDecimal("292.75"), "Annulation")).status()).isEqualTo(InvoiceStatus.CANCELLED);
    }

    @Test
    void overdueRemindersByLevel() {
        InvoiceDto i = service.issue(accountant, draft().id());
        assertCode(() -> service.remind(accountant, i.id(), 1), "REMINDER_NOT_DUE");
        jdbc.update("update invoices set due_date = current_date - 16 where id = ?", i.id());
        assertThat(service.get(accountant, i.id()).status()).isEqualTo(InvoiceStatus.OVERDUE);
        assertThat(service.remindersDue(accountant)).singleElement().satisfies(r -> assertThat(r.level()).isEqualTo(2));
        assertCode(() -> service.remind(accountant, i.id(), 3), "REMINDER_NOT_DUE");
        service.remind(accountant, i.id(), 2);
        assertCode(() -> service.remind(accountant, i.id(), 2), "REMINDER_ALREADY_SENT");
        assertThat(service.remindersDue(accountant)).isEmpty();
        assertThat(service.list(accountant, null, null, true)).hasSize(1);
        StatsDto s = service.stats(accountant, null);
        assertThat(s.overdueCount()).isEqualTo(1);
        assertThat(s.outstanding()).isEqualByComparingTo("292.75");
    }
}
