package fr.epita.platform.apps.invoice.service;

import fr.epita.platform.apps.invoice.dto.InvoiceDtos.*;
import fr.epita.platform.apps.invoice.entity.*;
import fr.epita.platform.apps.invoice.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.Sequences;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Une facture émise n'est plus jamais modifiée : corrections par avoir, encaissements par paiements.
 * Montants : HT et TVA arrondis au centime par ligne ; reste dû = TTC − paiements − avoirs.
 */
@Service
@RequiredArgsConstructor
public class InvoiceService {

    public static final String ROLES = "invoice_roles";
    public static final String ACCOUNTANT = "ACCOUNTANT";
    static final String INVOICE_SEQ = "invoice_sequences";
    static final String CREDIT_SEQ = "invoice_credit_sequences";
    /** Niveaux de relance : jours de retard après l'échéance. */
    static final int[] REMINDER_DAYS = {7, 15, 30};
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final InvoiceRepository invoices;
    private final InvoiceLineRepository lines;
    private final InvoicePaymentRepository payments;
    private final CreditNoteRepository credits;
    private final ReminderRepository reminders;
    private final Sequences sequences;
    private final AppRoles roles;
    private final UserLookup userLookup;
    private final Clock clock;

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(ACCOUNTANT));
    }

    // ---------- brouillons ----------

    @Transactional
    public InvoiceDto create(AuthUser me, InvoiceRequest req) {
        accountant(me);
        userLookup.require(req.clientId());
        Invoice i = new Invoice();
        i.setCreatedBy(me.id());
        apply(i, req);
        return detail(me, i);
    }

    @Transactional
    public InvoiceDto update(AuthUser me, UUID id, InvoiceRequest req) {
        accountant(me);
        Invoice i = invoices.lock(id).orElseThrow(() -> ApiException.notFound("Facture"));
        requireDraft(i);
        userLookup.require(req.clientId());
        lines.deleteByInvoiceId(id);
        lines.flush();
        apply(i, req);
        return detail(me, i);
    }

    private void apply(Invoice i, InvoiceRequest req) {
        i.setClientId(req.clientId());
        i.setDueDate(req.dueDate());
        BigDecimal ht = BigDecimal.ZERO;
        BigDecimal vat = BigDecimal.ZERO;
        for (LineRequest l : req.lines()) {
            BigDecimal lineHt = l.quantity().multiply(l.unitPrice()).setScale(2, RoundingMode.HALF_UP);
            ht = ht.add(lineHt);
            vat = vat.add(lineHt.multiply(l.vatRate()).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        }
        i.setTotalHt(ht.setScale(2, RoundingMode.HALF_UP));
        i.setTotalVat(vat.setScale(2, RoundingMode.HALF_UP));
        i.setTotalTtc(ht.add(vat).setScale(2, RoundingMode.HALF_UP));
        invoices.saveAndFlush(i);
        for (int p = 0; p < req.lines().size(); p++) {
            LineRequest r = req.lines().get(p);
            InvoiceLine l = new InvoiceLine();
            l.setInvoiceId(i.getId());
            l.setPosition(p);
            l.setLabel(r.label().trim());
            l.setQuantity(r.quantity());
            l.setUnitPrice(r.unitPrice());
            l.setVatRate(r.vatRate());
            lines.save(l);
        }
        lines.flush();
    }

    /** Émission : numéro continu de l'année, attribué sous verrou de la facture (pas de trou ni de doublon). */
    @Transactional
    public InvoiceDto issue(AuthUser me, UUID id) {
        accountant(me);
        Invoice i = invoices.lock(id).orElseThrow(() -> ApiException.notFound("Facture"));
        requireDraft(i);
        if (i.getDueDate().isBefore(today())) {
            throw ValidationException.field("dueDate", "ne doit pas être passée à l'émission");
        }
        i.setNumber(sequences.next(INVOICE_SEQ, "FAC", today().getYear()));
        i.setIssuedAt(clock.instant());
        return detail(me, invoices.saveAndFlush(i));
    }

    // ---------- consultation ----------

    @Transactional(readOnly = true)
    public List<InvoiceSummary> list(AuthUser me, InvoiceStatus status, UUID clientId, Boolean overdue) {
        List<Invoice> list = isAccountant(me) ? invoices.findAllByOrderByCreatedAtDesc()
                : invoices.findByClientIdAndIssuedAtNotNullOrderByIssuedAtDesc(me.id());
        return list.stream().filter(i -> clientId == null || i.getClientId().equals(clientId)).map(i -> {
            Money m = money(i);
            return new InvoiceSummary(i.getId(), i.getNumber(), i.getClientId(), userLookup.name(i.getClientId()), status(i, m),
                    i.getDueDate(), i.getIssuedAt(), i.getTotalTtc(), m.remaining());
        }).filter(s -> status == null || s.status() == status)
                .filter(s -> overdue == null || (s.status() == InvoiceStatus.OVERDUE) == overdue).toList();
    }

    @Transactional(readOnly = true)
    public InvoiceDto get(AuthUser me, UUID id) {
        Invoice i = invoices.findById(id).orElseThrow(() -> ApiException.notFound("Facture"));
        if (!isAccountant(me) && (i.getIssuedAt() == null || !i.getClientId().equals(me.id()))) {
            throw ApiException.notFound("Facture");
        }
        return detail(me, i);
    }

    // ---------- encaissements et avoirs ----------

    @Transactional
    public InvoiceDto pay(AuthUser me, UUID id, PaymentRequest req) {
        accountant(me);
        Invoice i = issued(id);
        if (req.date().isAfter(today())) {
            throw ValidationException.field("date", "ne doit pas être dans le futur");
        }
        Money m = money(i);
        if (req.amount().compareTo(m.remaining()) > 0) {
            throw ApiException.conflict("OVERPAYMENT", "Le paiement dépasse le reste dû (" + m.remaining() + " €)");
        }
        InvoicePayment p = new InvoicePayment();
        p.setInvoiceId(id);
        p.setPaidOn(req.date());
        p.setAmount(req.amount());
        p.setMethod(req.method());
        p.setCreatedBy(me.id());
        payments.saveAndFlush(p);
        return detail(me, i);
    }

    @Transactional
    public InvoiceDto credit(AuthUser me, UUID id, CreditRequest req) {
        accountant(me);
        Invoice i = issued(id);
        Money m = money(i);
        if (req.amount().compareTo(m.remaining()) > 0) {
            throw ApiException.conflict("CREDIT_TOO_HIGH", "L'avoir dépasse le reste dû (" + m.remaining() + " €)");
        }
        CreditNote c = new CreditNote();
        c.setInvoiceId(id);
        c.setNumber(sequences.next(CREDIT_SEQ, "AVO", today().getYear()));
        c.setAmount(req.amount());
        c.setReason(req.reason().trim());
        c.setCreatedBy(me.id());
        credits.saveAndFlush(c);
        return detail(me, i);
    }

    // ---------- relances ----------

    @Transactional(readOnly = true)
    public List<ReminderDueDto> remindersDue(AuthUser me) {
        accountant(me);
        List<ReminderDueDto> out = new ArrayList<>();
        for (Invoice i : invoices.findByIssuedAtNotNull()) {
            Integer level = reminderDue(i, money(i));
            if (level != null) {
                out.add(new ReminderDueDto(i.getId(), i.getNumber(), userLookup.name(i.getClientId()), i.getDueDate(),
                        daysOverdue(i), money(i).remaining(), level));
            }
        }
        out.sort(Comparator.comparingLong(ReminderDueDto::daysOverdue).reversed());
        return out;
    }

    @Transactional
    public InvoiceDto remind(AuthUser me, UUID id, int level) {
        accountant(me);
        Invoice i = issued(id);
        Money m = money(i);
        if (m.remaining().signum() == 0) {
            throw ApiException.conflict("REMINDER_NOT_DUE", "La facture est soldée");
        }
        if (daysOverdue(i) < REMINDER_DAYS[level - 1]) {
            throw ApiException.conflict("REMINDER_NOT_DUE", "Relance de niveau " + level + " possible à partir de J+" + REMINDER_DAYS[level - 1]);
        }
        if (reminders.findByInvoiceIdOrderByLevelAsc(id).stream().anyMatch(r -> r.getLevel() == level)) {
            throw ApiException.conflict("REMINDER_ALREADY_SENT", "Relance de niveau " + level + " déjà envoyée");
        }
        Reminder r = new Reminder();
        r.setInvoiceId(id);
        r.setLevel(level);
        r.setCreatedBy(me.id());
        reminders.saveAndFlush(r);
        return detail(me, i);
    }

    // ---------- tableau de bord ----------

    @Transactional(readOnly = true)
    public StatsDto stats(AuthUser me, Integer year) {
        accountant(me);
        int y = year == null ? today().getYear() : year;
        BigDecimal invoiced = BigDecimal.ZERO.setScale(2);
        BigDecimal credited = BigDecimal.ZERO.setScale(2);
        BigDecimal collected = BigDecimal.ZERO.setScale(2);
        BigDecimal outstanding = BigDecimal.ZERO.setScale(2);
        BigDecimal overdueAmount = BigDecimal.ZERO.setScale(2);
        long overdueCount = 0;
        for (Invoice i : invoices.findByIssuedAtNotNull()) {
            Money m = money(i);
            if (i.getIssuedAt().atZone(ZONE).getYear() == y) {
                invoiced = invoiced.add(i.getTotalTtc());
                credited = credited.add(m.credited());
            }
            collected = collected.add(payments.findByInvoiceIdOrderByPaidOnAsc(i.getId()).stream()
                    .filter(p -> p.getPaidOn().getYear() == y).map(InvoicePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            outstanding = outstanding.add(m.remaining());
            if (status(i, m) == InvoiceStatus.OVERDUE) {
                overdueCount++;
                overdueAmount = overdueAmount.add(m.remaining());
            }
        }
        return new StatsDto(y, invoiced, credited, collected, outstanding, overdueCount, overdueAmount);
    }

    // ---------- calculs ----------

    record Money(BigDecimal paid, BigDecimal credited, BigDecimal remaining) {
    }

    private Money money(Invoice i) {
        BigDecimal paid = payments.findByInvoiceIdOrderByPaidOnAsc(i.getId()).stream().map(InvoicePayment::getAmount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal credited = credits.findByInvoiceIdOrderByCreatedAtAsc(i.getId()).stream().map(CreditNote::getAmount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        return new Money(paid, credited, i.getTotalTtc().subtract(paid).subtract(credited));
    }

    InvoiceStatus status(Invoice i, Money m) {
        if (i.getIssuedAt() == null) {
            return InvoiceStatus.DRAFT;
        }
        if (m.credited().compareTo(i.getTotalTtc()) == 0) {
            return InvoiceStatus.CANCELLED;
        }
        if (m.remaining().signum() == 0) {
            return InvoiceStatus.PAID;
        }
        if (i.getDueDate().isBefore(today())) {
            return InvoiceStatus.OVERDUE;
        }
        return m.paid().signum() > 0 || m.credited().signum() > 0 ? InvoiceStatus.PARTIALLY_PAID : InvoiceStatus.ISSUED;
    }

    private long daysOverdue(Invoice i) {
        return Math.max(0, ChronoUnit.DAYS.between(i.getDueDate(), today()));
    }

    /** Plus haut niveau atteint non encore envoyé, si la facture est en retard. */
    private Integer reminderDue(Invoice i, Money m) {
        if (i.getIssuedAt() == null || status(i, m) != InvoiceStatus.OVERDUE) {
            return null;
        }
        long late = daysOverdue(i);
        Set<Integer> sent = new HashSet<>();
        reminders.findByInvoiceIdOrderByLevelAsc(i.getId()).forEach(r -> sent.add(r.getLevel()));
        for (int level = REMINDER_DAYS.length; level >= 1; level--) {
            if (late >= REMINDER_DAYS[level - 1]) {
                return sent.contains(level) ? null : level;
            }
        }
        return null;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private boolean isAccountant(AuthUser me) {
        return roles.has(ROLES, me, ACCOUNTANT);
    }

    private void accountant(AuthUser me) {
        roles.require(ROLES, me, ACCOUNTANT);
    }

    private void requireDraft(Invoice i) {
        if (i.getIssuedAt() != null) {
            throw ApiException.conflict("INVOICE_ISSUED", "Facture émise : elle n'est plus modifiable (corrigez-la par un avoir)");
        }
    }

    private Invoice issued(UUID id) {
        Invoice i = invoices.lock(id).orElseThrow(() -> ApiException.notFound("Facture"));
        if (i.getIssuedAt() == null) {
            throw ApiException.conflict("INVOICE_NOT_ISSUED", "La facture n'est pas encore émise");
        }
        return i;
    }

    private InvoiceDto detail(AuthUser me, Invoice i) {
        Money m = money(i);
        List<LineDto> ls = lines.findByInvoiceIdOrderByPositionAsc(i.getId()).stream().map(l -> {
            BigDecimal ht = l.getQuantity().multiply(l.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal vat = ht.multiply(l.getVatRate()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            return new LineDto(l.getPosition(), l.getLabel(), l.getQuantity(), l.getUnitPrice(), l.getVatRate(), ht, vat, ht.add(vat));
        }).toList();
        List<PaymentDto> ps = payments.findByInvoiceIdOrderByPaidOnAsc(i.getId()).stream()
                .map(p -> new PaymentDto(p.getId(), p.getPaidOn(), p.getAmount(), p.getMethod())).toList();
        List<CreditDto> cs = credits.findByInvoiceIdOrderByCreatedAtAsc(i.getId()).stream()
                .map(c -> new CreditDto(c.getId(), c.getNumber(), c.getAmount(), c.getReason(), c.getCreatedAt())).toList();
        List<ReminderDto> rs = isAccountant(me) ? reminders.findByInvoiceIdOrderByLevelAsc(i.getId()).stream()
                .map(r -> new ReminderDto(r.getLevel(), r.getCreatedAt(), r.getCreatedBy() == null ? null : userLookup.name(r.getCreatedBy()))).toList() : List.of();
        return new InvoiceDto(i.getId(), i.getNumber(), i.getClientId(), userLookup.name(i.getClientId()), status(i, m), i.getDueDate(),
                i.getIssuedAt(), ls, i.getTotalHt(), i.getTotalVat(), i.getTotalTtc(), m.paid(), m.credited(), m.remaining(),
                i.getIssuedAt() == null ? 0 : daysOverdue(i), ps, cs, rs, isAccountant(me) ? reminderDue(i, m) : null);
    }
}
