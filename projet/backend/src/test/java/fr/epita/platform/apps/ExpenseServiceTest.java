package fr.epita.platform.apps;

import fr.epita.platform.IntegrationTest;
import fr.epita.platform.apps.expense.dto.ExpenseDtos.ClaimDto;
import fr.epita.platform.apps.expense.dto.ExpenseDtos.ItemDecision;
import fr.epita.platform.apps.expense.dto.ExpenseDtos.ItemRequest;
import fr.epita.platform.apps.expense.entity.ExpenseEnums.Category;
import fr.epita.platform.apps.expense.entity.ExpenseEnums.ClaimStatus;
import fr.epita.platform.apps.expense.service.ExpenseService;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import fr.epita.platform.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExpenseServiceTest extends IntegrationTest {

    @Autowired
    ExpenseService service;
    @Autowired
    ManagerRelations relations;

    AuthUser admin;
    AuthUser marc;
    AuthUser alice;
    User aliceUser;
    LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        admin = auth(admin("Admin"));
        marc = auth(user("Marc"));
        aliceUser = user("Alice");
        alice = auth(aliceUser);
        relations.assign(ExpenseService.REPORTS, alice.id(), marc.id());
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("receipt", "ticket.pdf", "application/pdf", "%PDF-1.4 ticket".getBytes());
    }

    private ClaimDto add(Category c, String amount, String justification) {
        return service.addItem(alice, new ItemRequest(today, c, new BigDecimal(amount), "Dépense", justification), pdf());
    }

    private void assertCode(Runnable r, String code) {
        assertThatThrownBy(r::run).isInstanceOf(ApiException.class).extracting("code").isEqualTo(code);
    }

    @Test
    void capsRequireAJustificationAndOldExpensesAreRefused() {
        assertThatThrownBy(() -> add(Category.MEAL, "30.00", null)).isInstanceOf(ValidationException.class);
        assertThat(add(Category.MEAL, "30.00", "Repas client").items().get(0).overCap()).isTrue();
        assertCode(() -> service.addItem(alice, new ItemRequest(today.minusMonths(4), Category.OTHER, BigDecimal.TEN, "x", null), pdf()), "EXPENSE_TOO_OLD");
    }

    @Test
    void receiptTypeIsChecked() {
        var exe = new MockMultipartFile("receipt", "virus.exe", "application/octet-stream", new byte[]{1, 2});
        assertCode(() -> service.addItem(alice, new ItemRequest(today, Category.OTHER, BigDecimal.TEN, "x", null), exe), "INVALID_FILE");
    }

    @Test
    void submittedClaimsAreLockedAndEmptyOnesCannotBeSubmitted() {
        ClaimDto c = add(Category.TRANSPORT, "12.40", null);
        service.deleteItem(alice, c.items().get(0).id());
        assertCode(() -> service.submit(alice, c.id()), "REPORT_EMPTY");
        ClaimDto again = add(Category.TRANSPORT, "12.40", null);
        service.submit(alice, again.id());
        assertCode(() -> add(Category.OTHER, "5.00", null), "REPORT_LOCKED");
        assertCode(() -> service.deleteItem(alice, again.items().get(0).id()), "REPORT_LOCKED");
    }

    @Test
    void managerReviewsEachLineAndAdminReimburses() {
        add(Category.MEAL, "20.00", null);
        ClaimDto c = add(Category.HOTEL, "100.00", null);
        service.submit(alice, c.id());
        var lines = c.items();
        assertThatThrownBy(() -> service.review(marc, c.id(), List.of(new ItemDecision(lines.get(0).id(), true, null))))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.review(marc, c.id(), List.of(new ItemDecision(lines.get(0).id(), true, null),
                new ItemDecision(lines.get(1).id(), false, " ")))).isInstanceOf(ValidationException.class);
        assertCode(() -> service.review(alice, c.id(), List.of()), "FORBIDDEN");
        ClaimDto reviewed = service.review(marc, c.id(), List.of(new ItemDecision(lines.get(0).id(), true, null),
                new ItemDecision(lines.get(1).id(), false, "Pas de justificatif hôtel")));
        assertThat(reviewed.status()).isEqualTo(ClaimStatus.VALIDATED);
        assertThat(reviewed.reimbursable()).isEqualByComparingTo(lines.get(0).amount());
        assertCode(() -> service.reimburse(marc, c.id()), "FORBIDDEN");
        assertThat(service.reimburse(admin, c.id()).status()).isEqualTo(ClaimStatus.REIMBURSED);
    }

    @Test
    void allLinesRefusedRejectsTheClaim() {
        ClaimDto c = add(Category.OTHER, "8.00", null);
        service.submit(alice, c.id());
        ClaimDto r = service.review(marc, c.id(), List.of(new ItemDecision(c.items().get(0).id(), false, "Personnel")));
        assertThat(r.status()).isEqualTo(ClaimStatus.REJECTED);
    }

    @Test
    void multipartEndpointAndReceiptDownload() throws Exception {
        var data = new MockMultipartFile("data", "", "application/json",
                toJson(Map.of("date", today.toString(), "category", "MEAL", "amount", 18.5, "description", "Déjeuner")).getBytes());
        String body = mvc.perform(multipart("/api/expenses/items").file(data).file(pdf()).header("Authorization", bearer(aliceUser)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].receiptName").value("ticket.pdf"))
                .andReturn().getResponse().getContentAsString();
        String itemId = json.readTree(body).at("/items/0/id").asText();
        mvc.perform(get("/api/expenses/items/" + itemId + "/receipt").header("Authorization", bearer(aliceUser)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/expenses/items/" + itemId + "/receipt").header("Authorization", bearer(user("Bob"))))
                .andExpect(status().isNotFound());
    }
}
