package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.quote.dto.QuoteDtos.LineRequest;
import fr.epita.platform.apps.quote.dto.QuoteDtos.ProductDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.ProductRequest;
import fr.epita.platform.apps.quote.dto.QuoteDtos.QuoteDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.QuoteRequest;
import fr.epita.platform.apps.quote.entity.QuoteStatus;
import fr.epita.platform.apps.quote.service.QuoteService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.relation.AppRoles;
import fr.epita.platform.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QuoteServiceTest extends IntegrationTest {

    @Autowired
    QuoteService service;
    @Autowired
    AppRoles roles;

    AuthUser admin;
    AuthUser sales;
    AuthUser client;
    User salesUser;
    ProductDto game;
    ProductDto book;

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        salesUser = user("Sam Vendeur");
        sales = auth(salesUser);
        client = auth(user("Clara Cliente"));
        roles.grant(QuoteService.ROLES, Set.of(QuoteService.SALES), salesUser.getId(), QuoteService.SALES);
        game = service.createProduct(admin, new ProductRequest("JEU-1", "Jeu de plateau", "Jeux", new BigDecimal("40.00"), new BigDecimal("20")));
        book = service.createProduct(admin, new ProductRequest("LIV-1", "Livre de règles", "Livres", new BigDecimal("10.00"), new BigDecimal("5.5")));
    }

    private QuoteDto draft() {
        return service.create(sales, new QuoteRequest(client.id(), "Ludothèque", List.of(
                new LineRequest(game.id(), 3, new BigDecimal("10")),
                new LineRequest(book.id(), 2, null))));
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void totalsAreComputedPerVatRate() {
        QuoteDto q = draft();
        // 3 × 40 × 0,9 = 108 ; 2 × 10 = 20 → HT 128 ; TVA 21,60 + 1,10 ; TTC 150,70
        assertThat(q.totals().totalExclTax()).isEqualByComparingTo("128.00");
        assertThat(q.totals().totalVat()).isEqualByComparingTo("22.70");
        assertThat(q.totals().totalInclTax()).isEqualByComparingTo("150.70");
        assertThat(q.totals().vat()).hasSize(2);
    }

    @Test
    void pricesAreFrozenWhenSentAndNumbersAreContinuous() {
        QuoteDto first = service.send(sales, draft().id(), LocalDate.now().plusDays(30));
        QuoteDto second = service.send(sales, draft().id(), LocalDate.now().plusDays(30));
        int year = LocalDate.now().getYear();
        assertThat(first.number()).isEqualTo("DEV-" + year + "-0001");
        assertThat(second.number()).isEqualTo("DEV-" + year + "-0002");

        service.updateProduct(admin, game.id(), new ProductRequest("JEU-1", "Jeu renommé", "Jeux", new BigDecimal("99.00"), new BigDecimal("20")));
        QuoteDto reread = service.get(sales, first.id());
        assertThat(reread.lines().get(0).unitPrice()).isEqualByComparingTo("40.00");
        assertThat(reread.lines().get(0).label()).isEqualTo("Jeu de plateau");
        assertThat(reread.totals().totalInclTax()).isEqualByComparingTo("150.70");
    }

    @Test
    void onlyDraftsAreEditableAndEmptyQuotesCannotBeSent() {
        QuoteDto empty = service.create(sales, new QuoteRequest(client.id(), "Vide", List.of()));
        assertCode(() -> service.send(sales, empty.id(), LocalDate.now().plusDays(5)), "QUOTE_EMPTY");
        QuoteDto sent = service.send(sales, draft().id(), LocalDate.now().plusDays(5));
        assertCode(() -> service.update(sales, sent.id(), new QuoteRequest(client.id(), "x", List.of())), "QUOTE_LOCKED");
        assertCode(() -> service.delete(sales, sent.id()), "QUOTE_LOCKED");
    }

    @Test
    void clientAcceptsOrRefusesOnceAndOnlyBeforeExpiry() {
        QuoteDto sent = service.send(sales, draft().id(), LocalDate.now().plusDays(5));
        assertThatThrownBy(() -> service.accept(sales, sent.id())).isInstanceOf(ApiException.class);
        assertThat(service.accept(client, sent.id()).status()).isEqualTo(QuoteStatus.ACCEPTED);
        assertCode(() -> service.refuse(client, sent.id(), "trop tard"), "QUOTE_LOCKED");

        QuoteDto other = service.send(sales, draft().id(), LocalDate.now().plusDays(5));
        jdbc.update("update quotes set valid_until = current_date - 1 where id = ?", other.id());
        assertThat(service.get(client, other.id()).status()).isEqualTo(QuoteStatus.EXPIRED);
        assertCode(() -> service.accept(client, other.id()), "QUOTE_EXPIRED");
    }

    @Test
    void clientSeesOnlySentQuotesAddressedToHim() {
        QuoteDto d = draft();
        assertThat(service.list(client, null, null)).isEmpty();
        assertCode(() -> service.get(client, d.id()), "NOT_FOUND");
        service.send(sales, d.id(), LocalDate.now().plusDays(5));
        assertThat(service.list(client, null, null)).hasSize(1);
    }

    @Test
    void archivedProductsAreRefusedInNewLinesButKeptInOldQuotes() {
        QuoteDto sent = service.send(sales, draft().id(), LocalDate.now().plusDays(5));
        service.toggleArchive(admin, book.id());
        assertThat(service.products(null, null, null)).extracting(ProductDto::id).doesNotContain(book.id());
        assertThatThrownBy(() -> service.create(sales, new QuoteRequest(client.id(), "x",
                List.of(new LineRequest(book.id(), 1, null))))).isInstanceOf(fr.epita.platform.common.ValidationException.class);
        assertThat(service.get(sales, sent.id()).lines()).hasSize(2);
        assertThat(service.duplicate(sales, sent.id()).lines()).hasSize(1);
    }

    @Test
    void quotesAreReservedToSalesRole() throws Exception {
        assertCode(() -> service.create(client, new QuoteRequest(sales.id(), "x", List.of())), "FORBIDDEN");
        mvc.perform(get("/api/quotes/stats").header("Authorization", bearer(userRepository.findById(client.id()).orElseThrow())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/quotes/stats").header("Authorization", bearer(salesUser)))
                .andExpect(status().isOk());
    }
}
