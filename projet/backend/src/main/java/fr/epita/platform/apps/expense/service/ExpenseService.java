package fr.epita.platform.apps.expense.service;

import fr.epita.platform.apps.expense.dto.ExpenseDtos.ClaimDto;
import fr.epita.platform.apps.expense.dto.ExpenseDtos.ClaimSummaryDto;
import fr.epita.platform.apps.expense.dto.ExpenseDtos.ItemDecision;
import fr.epita.platform.apps.expense.dto.ExpenseDtos.ItemDto;
import fr.epita.platform.apps.expense.dto.ExpenseDtos.ItemRequest;
import fr.epita.platform.apps.expense.dto.ExpenseDtos.StatsDto;
import fr.epita.platform.apps.expense.entity.ExpenseClaim;
import fr.epita.platform.apps.expense.entity.ExpenseEnums.Category;
import fr.epita.platform.apps.expense.entity.ExpenseEnums.ClaimStatus;
import fr.epita.platform.apps.expense.entity.ExpenseItem;
import fr.epita.platform.apps.expense.repository.ExpenseClaimRepository;
import fr.epita.platform.apps.expense.repository.ExpenseItemRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.FileStorage;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    public static final String REPORTS = "expense_reports";
    static final int MAX_AGE_MONTHS = 3;

    private final ExpenseClaimRepository claims;
    private final ExpenseItemRepository items;
    private final ManagerRelations relations;
    private final FileStorage storage;
    private final UserLookup userLookup;
    private final Clock clock;

    public record Receipt(byte[] content, String contentType, String fileName) {
    }

    // ---------- dépenses ----------

    @Transactional
    public ClaimDto addItem(AuthUser me, ItemRequest req, MultipartFile receipt) {
        validate(req);
        ExpenseClaim claim = draftFor(me.id(), YearMonth.from(req.date()).toString());
        ExpenseItem item = new ExpenseItem();
        item.setClaim(claim);
        apply(item, req);
        attach(item, receipt);
        claim.getItems().add(item);
        return toDto(claims.saveAndFlush(claim), me);
    }

    /** Modification d'une dépense d'une note en brouillon ; le justificatif est facultatif (sinon conservé). */
    @Transactional
    public ClaimDto updateItem(AuthUser me, UUID itemId, ItemRequest req, MultipartFile receipt) {
        validate(req);
        ExpenseItem item = ownItem(me, itemId);
        String month = YearMonth.from(req.date()).toString();
        if (!item.getClaim().getMonth().equals(month)) {
            // la dépense change de mois : elle passe dans la note de ce mois (en brouillon)
            ExpenseClaim target = draftFor(me.id(), month);
            item.getClaim().getItems().remove(item);
            ExpenseItem moved = new ExpenseItem();
            moved.setClaim(target);
            moved.setReceiptKey(item.getReceiptKey());
            moved.setReceiptType(item.getReceiptType());
            moved.setReceiptName(item.getReceiptName());
            item = moved;
            target.getItems().add(item);
        }
        apply(item, req);
        if (receipt != null && !receipt.isEmpty()) {
            attach(item, receipt);
        }
        ExpenseClaim claim = claims.saveAndFlush(item.getClaim());
        return toDto(claim, me);
    }

    @Transactional
    public ClaimDto deleteItem(AuthUser me, UUID itemId) {
        ExpenseItem item = ownItem(me, itemId);
        ExpenseClaim claim = item.getClaim();
        claim.getItems().remove(item);
        return toDto(claims.saveAndFlush(claim), me);
    }

    @Transactional(readOnly = true)
    public Receipt receipt(AuthUser me, UUID itemId) {
        ExpenseItem item = items.findById(itemId).orElseThrow(() -> ApiException.notFound("Dépense"));
        requireReader(me, item.getClaim());
        return new Receipt(storage.load(item.getReceiptKey()), item.getReceiptType(), item.getReceiptName());
    }

    // ---------- notes ----------

    @Transactional(readOnly = true)
    public List<ClaimSummaryDto> mine(AuthUser me) {
        return claims.findByUserIdOrderByMonthDesc(me.id()).stream().map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public ClaimDto get(AuthUser me, UUID id) {
        ExpenseClaim c = claim(id);
        requireReader(me, c);
        return toDto(c, me);
    }

    @Transactional
    public ClaimDto submit(AuthUser me, UUID id) {
        ExpenseClaim c = claim(id);
        if (!c.getUserId().equals(me.id())) {
            throw ApiException.forbidden("Seul l'auteur soumet sa note");
        }
        if (c.getStatus() != ClaimStatus.DRAFT) {
            throw ApiException.conflict("REPORT_LOCKED", "Cette note a déjà été soumise");
        }
        if (c.getItems().isEmpty()) {
            throw ApiException.conflict("REPORT_EMPTY", "Une note vide ne peut pas être soumise");
        }
        c.setStatus(ClaimStatus.SUBMITTED);
        c.setSubmittedAt(clock.instant());
        return toDto(c, me);
    }

    @Transactional(readOnly = true)
    public List<ClaimSummaryDto> team(AuthUser me, ClaimStatus status) {
        List<ExpenseClaim> list = me.isAdmin() ? claims.findAllByOrderByMonthDesc()
                : claims.findByUserIdInOrderByMonthDesc(relations.employeesOf(REPORTS, me.id()));
        return list.stream().filter(c -> !c.getUserId().equals(me.id()))
                .filter(c -> status == null ? c.getStatus() != ClaimStatus.DRAFT : c.getStatus() == status)
                .map(this::summary).toList();
    }

    /** Le manager décide de chaque ligne ; la note est validée si au moins une ligne est acceptée. */
    @Transactional
    public ClaimDto review(AuthUser me, UUID id, List<ItemDecision> decisions) {
        ExpenseClaim c = claim(id);
        if (c.getUserId().equals(me.id())) {
            throw ApiException.forbidden("On ne valide pas sa propre note");
        }
        if (!me.isAdmin() && !relations.isManagerOf(REPORTS, me.id(), c.getUserId())) {
            throw ApiException.forbidden("Seul le manager du salarié valide sa note");
        }
        if (c.getStatus() != ClaimStatus.SUBMITTED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Seule une note soumise peut être revue");
        }
        Map<UUID, ItemDecision> byItem = new HashMap<>();
        var errors = new ValidationException.Collector();
        for (int i = 0; i < decisions.size(); i++) {
            ItemDecision d = decisions.get(i);
            byItem.put(d.itemId(), d);
            errors.check(d.accepted() || (d.reason() != null && !d.reason().isBlank()), "items[" + i + "].reason",
                    "est obligatoire pour une ligne refusée");
        }
        Set<UUID> expected = new HashSet<>(c.getItems().stream().map(ExpenseItem::getId).toList());
        errors.check(expected.equals(byItem.keySet()), "items", "une décision est attendue pour chaque ligne de la note");
        errors.throwIfAny();
        boolean anyAccepted = false;
        for (ExpenseItem item : c.getItems()) {
            ItemDecision d = byItem.get(item.getId());
            item.setAccepted(d.accepted());
            item.setRejectReason(d.accepted() ? null : d.reason().trim());
            anyAccepted |= d.accepted();
        }
        c.setStatus(anyAccepted ? ClaimStatus.VALIDATED : ClaimStatus.REJECTED);
        c.setReviewedBy(me.id());
        c.setReviewedAt(clock.instant());
        return toDto(c, me);
    }

    @Transactional
    public ClaimDto reimburse(AuthUser me, UUID id) {
        me.requireAdmin();
        ExpenseClaim c = claim(id);
        if (c.getStatus() != ClaimStatus.VALIDATED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Seule une note validée peut être remboursée");
        }
        c.setStatus(ClaimStatus.REIMBURSED);
        c.setReimbursedAt(clock.instant());
        return toDto(c, me);
    }

    @Transactional(readOnly = true)
    public List<ClaimSummaryDto> toReimburse(AuthUser me) {
        me.requireAdmin();
        return claims.findAllByOrderByMonthDesc().stream()
                .filter(c -> c.getStatus() == ClaimStatus.VALIDATED || c.getStatus() == ClaimStatus.REIMBURSED)
                .map(this::summary).toList();
    }

    @Transactional(readOnly = true)
    public StatsDto stats(AuthUser me, Integer year) {
        int y = year == null ? LocalDate.now(clock).getYear() : year;
        List<ExpenseClaim> source = me.isAdmin() ? claims.findAllByOrderByMonthDesc() : claims.findByUserIdOrderByMonthDesc(me.id());
        Map<Category, BigDecimal> byCategory = new EnumMap<>(Category.class);
        for (Category cat : Category.values()) {
            byCategory.put(cat, BigDecimal.ZERO);
        }
        Map<String, BigDecimal> byMonth = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ExpenseClaim c : source) {
            if (!c.getMonth().startsWith(y + "-")) {
                continue;
            }
            for (ExpenseItem i : c.getItems()) {
                if (Boolean.FALSE.equals(i.getAccepted())) {
                    continue;
                }
                byCategory.merge(i.getCategory(), i.getAmount(), BigDecimal::add);
                byMonth.merge(c.getMonth(), i.getAmount(), BigDecimal::add);
                total = total.add(i.getAmount());
            }
        }
        return new StatsDto(y, byCategory, byMonth, total);
    }

    // ---------- règles ----------

    void validate(ItemRequest req) {
        LocalDate today = LocalDate.now(clock);
        var errors = new ValidationException.Collector();
        errors.check(!req.date().isAfter(today), "date", "doit être une date dans le passé ou aujourd'hui");
        BigDecimal cap = req.category().cap();
        boolean overCap = cap != null && req.amount().compareTo(cap) > 0;
        errors.check(!overCap || (req.justification() != null && !req.justification().isBlank()), "justification",
                "est obligatoire au-delà du plafond de " + cap + " €");
        errors.throwIfAny();
        if (req.date().isBefore(today.minusMonths(MAX_AGE_MONTHS))) {
            throw ApiException.conflict("EXPENSE_TOO_OLD", "Une dépense de plus de 3 mois ne peut plus être déclarée");
        }
    }

    private ExpenseClaim draftFor(UUID userId, String month) {
        ExpenseClaim claim = claims.findByUserIdAndMonth(userId, month).orElseGet(() -> {
            ExpenseClaim c = new ExpenseClaim();
            c.setUserId(userId);
            c.setMonth(month);
            return claims.saveAndFlush(c);
        });
        if (claim.getStatus() != ClaimStatus.DRAFT) {
            throw ApiException.conflict("REPORT_LOCKED", "La note de " + month + " est déjà soumise : elle n'est plus modifiable");
        }
        return claim;
    }

    private ExpenseItem ownItem(AuthUser me, UUID itemId) {
        ExpenseItem item = items.findById(itemId).orElseThrow(() -> ApiException.notFound("Dépense"));
        if (!item.getClaim().getUserId().equals(me.id())) {
            throw ApiException.forbidden("Seul l'auteur modifie ses dépenses");
        }
        if (item.getClaim().getStatus() != ClaimStatus.DRAFT) {
            throw ApiException.conflict("REPORT_LOCKED", "Cette note est soumise : elle n'est plus modifiable");
        }
        return item;
    }

    private void attach(ExpenseItem item, MultipartFile receipt) {
        FileStorage.Stored stored = storage.store(receipt, FileStorage.DOCUMENTS);
        item.setReceiptKey(stored.key());
        item.setReceiptType(stored.contentType());
        item.setReceiptName(stored.originalName());
    }

    private static void apply(ExpenseItem item, ItemRequest req) {
        item.setExpenseDate(req.date());
        item.setCategory(req.category());
        item.setAmount(req.amount());
        item.setDescription(req.description().trim());
        item.setJustification(req.justification() == null || req.justification().isBlank() ? null : req.justification().trim());
    }

    private void requireReader(AuthUser me, ExpenseClaim c) {
        if (!me.isAdmin() && !c.getUserId().equals(me.id()) && !relations.isManagerOf(REPORTS, me.id(), c.getUserId())) {
            throw ApiException.notFound("Note de frais");
        }
    }

    private ExpenseClaim claim(UUID id) {
        return claims.findById(id).orElseThrow(() -> ApiException.notFound("Note de frais"));
    }

    private static BigDecimal total(ExpenseClaim c) {
        return c.getItems().stream().map(ExpenseItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal reimbursable(ExpenseClaim c) {
        return c.getItems().stream().filter(i -> Boolean.TRUE.equals(i.getAccepted()))
                .map(ExpenseItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private ClaimSummaryDto summary(ExpenseClaim c) {
        return new ClaimSummaryDto(c.getId(), c.getUserId(), userLookup.name(c.getUserId()), c.getMonth(), c.getStatus(),
                c.getItems().size(), total(c), reimbursable(c));
    }

    private ClaimDto toDto(ExpenseClaim c, AuthUser me) {
        List<ItemDto> list = c.getItems().stream().map(i -> new ItemDto(i.getId(), i.getExpenseDate(), i.getCategory(),
                i.getAmount(), i.getDescription(), i.getJustification(),
                i.getCategory().cap() != null && i.getAmount().compareTo(i.getCategory().cap()) > 0,
                i.getReceiptName(), i.getReceiptType(), i.getAccepted(), i.getRejectReason())).toList();
        return new ClaimDto(c.getId(), c.getUserId(), userLookup.name(c.getUserId()), c.getMonth(), c.getStatus(), list,
                total(c), reimbursable(c), c.getSubmittedAt(), userLookup.name(c.getReviewedBy()), c.getReviewedAt(),
                c.getReimbursedAt(), c.getStatus() == ClaimStatus.DRAFT && c.getUserId().equals(me.id()));
    }
}
