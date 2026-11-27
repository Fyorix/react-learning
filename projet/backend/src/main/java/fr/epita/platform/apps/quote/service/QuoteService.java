package fr.epita.platform.apps.quote.service;

import fr.epita.platform.apps.quote.dto.QuoteDtos.ClientDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.LineDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.LineRequest;
import fr.epita.platform.apps.quote.dto.QuoteDtos.ProductDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.ProductRequest;
import fr.epita.platform.apps.quote.dto.QuoteDtos.QuoteDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.QuoteRequest;
import fr.epita.platform.apps.quote.dto.QuoteDtos.QuoteStatsDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.QuoteSummaryDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.TotalsDto;
import fr.epita.platform.apps.quote.dto.QuoteDtos.VatDto;
import fr.epita.platform.apps.quote.entity.Product;
import fr.epita.platform.apps.quote.entity.Quote;
import fr.epita.platform.apps.quote.entity.QuoteLine;
import fr.epita.platform.apps.quote.entity.QuoteStatus;
import fr.epita.platform.apps.quote.repository.ProductRepository;
import fr.epita.platform.apps.quote.repository.QuoteRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.Sequences;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import fr.epita.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuoteService {

    public static final String ROLES = "quote_roles";
    public static final String SALES = "SALES";
    private static final Set<BigDecimal> VAT_RATES = Set.of(new BigDecimal("0"), new BigDecimal("5.5"),
            new BigDecimal("10"), new BigDecimal("20"));
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProductRepository products;
    private final QuoteRepository quotes;
    private final UserRepository users;
    private final UserLookup userLookup;
    private final AppRoles roles;
    private final Sequences sequences;
    private final Clock clock;

    // ---------- catalogue ----------

    @Transactional(readOnly = true)
    public List<ProductDto> products(String search, String category, Boolean archived) {
        String term = search == null ? null : search.trim().toLowerCase(Locale.ROOT);
        return products.findAllByOrderByCategoryAscNameAsc().stream()
                .filter(p -> archived == null ? !p.isArchived() : p.isArchived() == archived)
                .filter(p -> category == null || category.isBlank() || p.getCategory().equalsIgnoreCase(category))
                .filter(p -> term == null || term.isEmpty() || p.getName().toLowerCase(Locale.ROOT).contains(term)
                        || p.getReference().toLowerCase(Locale.ROOT).contains(term))
                .map(QuoteService::toDto).toList();
    }

    @Transactional
    public ProductDto createProduct(AuthUser me, ProductRequest req) {
        me.requireAdmin();
        checkVat(req.vatRate());
        if (products.existsByReferenceIgnoreCase(req.reference().trim())) {
            throw ApiException.conflict("PRODUCT_REFERENCE_EXISTS", "Cette référence existe déjà");
        }
        Product p = new Product();
        apply(p, req);
        return toDto(products.saveAndFlush(p));
    }

    @Transactional
    public ProductDto updateProduct(AuthUser me, UUID id, ProductRequest req) {
        me.requireAdmin();
        checkVat(req.vatRate());
        Product p = product(id);
        if (products.existsByReferenceIgnoreCaseAndIdNot(req.reference().trim(), id)) {
            throw ApiException.conflict("PRODUCT_REFERENCE_EXISTS", "Cette référence existe déjà");
        }
        apply(p, req);
        return toDto(p);
    }

    @Transactional
    public ProductDto toggleArchive(AuthUser me, UUID id) {
        me.requireAdmin();
        Product p = product(id);
        p.setArchived(!p.isArchived());
        return toDto(p);
    }

    // ---------- devis ----------

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(SALES));
    }

    @Transactional(readOnly = true)
    public List<ClientDto> clients(AuthUser me) {
        requireSales(me);
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream()
                .filter(u -> !u.getId().equals(me.id()))
                .map(u -> new ClientDto(u.getId(), u.getDisplayName())).toList();
    }

    @Transactional
    public QuoteDto create(AuthUser me, QuoteRequest req) {
        requireSales(me);
        Quote q = new Quote();
        q.setSalesId(me.id());
        q.setUpdatedAt(clock.instant());
        applyDraft(q, req, me);
        return toDto(quotes.saveAndFlush(q), me);
    }

    @Transactional(readOnly = true)
    public List<QuoteSummaryDto> list(AuthUser me, QuoteStatus status, UUID clientId) {
        LocalDate today = today();
        List<Quote> source;
        if (me.isAdmin()) {
            source = quotes.findAllByOrderByCreatedAtDesc();
        } else if (isSales(me)) {
            source = quotes.findBySalesIdOrderByCreatedAtDesc(me.id());
        } else {
            source = quotes.findByClientIdOrderByCreatedAtDesc(me.id()).stream()
                    .filter(q -> q.getStatus() != QuoteStatus.DRAFT).toList();
        }
        var names = userLookup.names(source.stream().flatMap(q -> java.util.stream.Stream.of(q.getSalesId(), q.getClientId())).toList());
        return source.stream()
                .filter(q -> status == null || q.effectiveStatus(today) == status)
                .filter(q -> clientId == null || q.getClientId().equals(clientId))
                .map(q -> new QuoteSummaryDto(q.getId(), q.getNumber(), q.getTitle(), q.effectiveStatus(today),
                        names.get(q.getSalesId()), names.get(q.getClientId()), q.getValidUntil(),
                        totals(q).totalInclTax(), q.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public QuoteDto get(AuthUser me, UUID id) {
        Quote q = quote(id);
        requireReader(me, q);
        return toDto(q, me);
    }

    @Transactional
    public QuoteDto update(AuthUser me, UUID id, QuoteRequest req) {
        Quote q = ownedDraft(me, id);
        applyDraft(q, req, me);
        q.setUpdatedAt(clock.instant());
        return toDto(quotes.saveAndFlush(q), me);
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        quotes.delete(ownedDraft(me, id));
    }

    /** Copie en nouveau brouillon, aux prix actuels du catalogue (les produits archivés sont ignorés). */
    @Transactional
    public QuoteDto duplicate(AuthUser me, UUID id) {
        requireSales(me);
        Quote source = quote(id);
        if (!me.isAdmin() && !source.getSalesId().equals(me.id())) {
            throw ApiException.forbidden("Seul le commercial du devis peut le dupliquer");
        }
        Quote copy = new Quote();
        copy.setSalesId(me.id());
        copy.setClientId(source.getClientId());
        copy.setTitle(source.getTitle());
        copy.setUpdatedAt(clock.instant());
        int pos = 0;
        for (QuoteLine l : source.getLines()) {
            if (l.getProduct().isArchived()) {
                continue;
            }
            copy.getLines().add(line(copy, l.getProduct(), l.getQuantity(), l.getDiscount(), pos++));
        }
        return toDto(quotes.saveAndFlush(copy), me);
    }

    @Transactional
    public QuoteDto send(AuthUser me, UUID id, LocalDate validUntil) {
        Quote q = ownedDraft(me, id);
        if (!validUntil.isAfter(today())) {
            throw ValidationException.field("validUntil", "doit être une date dans le futur");
        }
        if (q.getLines().isEmpty()) {
            throw ApiException.conflict("QUOTE_EMPTY", "Un devis sans ligne ne peut pas être envoyé");
        }
        q.getLines().forEach(QuoteLine::freeze);
        q.setNumber(sequences.next("quote_sequences", "DEV", today().getYear()));
        q.setValidUntil(validUntil);
        q.setStatus(QuoteStatus.SENT);
        q.setSentAt(clock.instant());
        q.setUpdatedAt(clock.instant());
        return toDto(q, me);
    }

    @Transactional
    public QuoteDto accept(AuthUser me, UUID id) {
        Quote q = decidable(me, id);
        q.setStatus(QuoteStatus.ACCEPTED);
        q.setDecidedAt(clock.instant());
        q.setUpdatedAt(clock.instant());
        return toDto(q, me);
    }

    @Transactional
    public QuoteDto refuse(AuthUser me, UUID id, String comment) {
        Quote q = decidable(me, id);
        q.setStatus(QuoteStatus.REFUSED);
        q.setRefusalComment(comment.trim());
        q.setDecidedAt(clock.instant());
        q.setUpdatedAt(clock.instant());
        return toDto(q, me);
    }

    @Transactional(readOnly = true)
    public QuoteStatsDto stats(AuthUser me, YearMonth month) {
        requireSales(me);
        YearMonth m = month == null ? YearMonth.now(clock) : month;
        ZoneId zone = clock.getZone();
        LocalDate today = today();
        List<Quote> mine = me.isAdmin() ? quotes.findAllByOrderByCreatedAtDesc()
                : quotes.findBySalesIdOrderByCreatedAtDesc(me.id());
        List<Quote> sent = mine.stream().filter(q -> q.getSentAt() != null
                && YearMonth.from(q.getSentAt().atZone(zone)).equals(m)).toList();
        Map<QuoteStatus, Long> byStatus = new EnumMap<>(QuoteStatus.class);
        for (QuoteStatus s : QuoteStatus.values()) {
            byStatus.put(s, 0L);
        }
        mine.stream().filter(q -> YearMonth.from(q.getCreatedAt().atZone(zone)).equals(m))
                .forEach(q -> byStatus.merge(q.effectiveStatus(today), 1L, Long::sum));
        BigDecimal accepted = sent.stream().filter(q -> q.getStatus() == QuoteStatus.ACCEPTED)
                .map(q -> totals(q).totalExclTax()).reduce(BigDecimal.ZERO, BigDecimal::add);
        long decided = sent.stream().filter(q -> q.effectiveStatus(today) != QuoteStatus.SENT).count();
        long won = sent.stream().filter(q -> q.getStatus() == QuoteStatus.ACCEPTED).count();
        Integer rate = decided == 0 ? null : (int) Math.round(won * 100.0 / decided);
        return new QuoteStatsDto(m.toString(), byStatus, accepted, decided, rate);
    }

    // ---------- règles ----------

    /** Totaux calculés côté serveur : HT arrondi au centime par ligne, TVA par taux. */
    public static TotalsDto totals(Quote q) {
        BigDecimal ht = BigDecimal.ZERO;
        Map<BigDecimal, BigDecimal> bases = new TreeMap<>();
        for (QuoteLine l : q.getLines()) {
            BigDecimal lineHt = lineTotal(l);
            ht = ht.add(lineHt);
            bases.merge(l.effectiveVatRate().stripTrailingZeros(), lineHt, BigDecimal::add);
        }
        List<VatDto> vat = new ArrayList<>();
        BigDecimal totalVat = BigDecimal.ZERO;
        for (var e : bases.entrySet()) {
            BigDecimal amount = e.getValue().multiply(e.getKey()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            vat.add(new VatDto(e.getKey(), e.getValue(), amount));
            totalVat = totalVat.add(amount);
        }
        return new TotalsDto(ht.setScale(2, RoundingMode.HALF_UP), vat, totalVat.setScale(2, RoundingMode.HALF_UP),
                ht.add(totalVat).setScale(2, RoundingMode.HALF_UP));
    }

    static BigDecimal lineTotal(QuoteLine l) {
        BigDecimal factor = HUNDRED.subtract(l.getDiscount()).divide(HUNDRED, 6, RoundingMode.HALF_UP);
        return l.effectiveUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity())).multiply(factor)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private void applyDraft(Quote q, QuoteRequest req, AuthUser me) {
        if (req.clientId().equals(me.id())) {
            throw ValidationException.field("clientId", "le client doit être différent du commercial");
        }
        if (!users.existsById(req.clientId())) {
            throw ApiException.notFound("Client");
        }
        q.setClientId(req.clientId());
        q.setTitle(req.title().trim());
        q.getLines().clear();
        int pos = 0;
        for (LineRequest l : req.lines()) {
            Product p = products.findById(l.productId()).orElseThrow(() -> ApiException.notFound("Produit"));
            if (p.isArchived()) {
                throw ValidationException.field("lines[" + pos + "].productId", "ce produit est archivé");
            }
            q.getLines().add(line(q, p, l.quantity(), l.discount() == null ? BigDecimal.ZERO : l.discount(), pos++));
        }
    }

    private static QuoteLine line(Quote q, Product p, int quantity, BigDecimal discount, int position) {
        QuoteLine line = new QuoteLine();
        line.setQuote(q);
        line.setProduct(p);
        line.setQuantity(quantity);
        line.setDiscount(discount);
        line.setPosition(position);
        return line;
    }

    private Quote ownedDraft(AuthUser me, UUID id) {
        requireSales(me);
        Quote q = quote(id);
        if (!me.isAdmin() && !q.getSalesId().equals(me.id())) {
            throw ApiException.forbidden("Seul le commercial du devis peut le modifier");
        }
        if (q.getStatus() != QuoteStatus.DRAFT) {
            throw ApiException.conflict("QUOTE_LOCKED", "Seul un devis en brouillon est modifiable");
        }
        return q;
    }

    private Quote decidable(AuthUser me, UUID id) {
        Quote q = quote(id);
        if (!q.getClientId().equals(me.id())) {
            throw ApiException.forbidden("Seul le client destinataire peut répondre au devis");
        }
        QuoteStatus s = q.effectiveStatus(today());
        if (s == QuoteStatus.EXPIRED) {
            throw ApiException.conflict("QUOTE_EXPIRED", "La date de validité du devis est dépassée");
        }
        if (s != QuoteStatus.SENT) {
            throw ApiException.conflict("QUOTE_LOCKED", "Ce devis a déjà reçu une réponse");
        }
        return q;
    }

    private void requireReader(AuthUser me, Quote q) {
        boolean client = q.getClientId().equals(me.id()) && q.getStatus() != QuoteStatus.DRAFT;
        if (!me.isAdmin() && !q.getSalesId().equals(me.id()) && !client) {
            throw ApiException.notFound("Devis");
        }
    }

    private boolean isSales(AuthUser me) {
        return roles.has(ROLES, me, SALES);
    }

    private void requireSales(AuthUser me) {
        roles.require(ROLES, me, SALES);
    }

    private static void checkVat(BigDecimal rate) {
        if (VAT_RATES.stream().noneMatch(r -> r.compareTo(rate) == 0)) {
            throw ValidationException.field("vatRate", "doit valoir 0, 5.5, 10 ou 20");
        }
    }

    private static void apply(Product p, ProductRequest req) {
        p.setReference(req.reference().trim().toUpperCase(Locale.ROOT));
        p.setName(req.name().trim());
        p.setCategory(req.category().trim());
        p.setUnitPrice(req.unitPrice().setScale(2, RoundingMode.HALF_UP));
        p.setVatRate(req.vatRate().setScale(1, RoundingMode.HALF_UP));
    }

    private Product product(UUID id) {
        return products.findById(id).orElseThrow(() -> ApiException.notFound("Produit"));
    }

    private Quote quote(UUID id) {
        return quotes.findById(id).orElseThrow(() -> ApiException.notFound("Devis"));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private static ProductDto toDto(Product p) {
        return new ProductDto(p.getId(), p.getReference(), p.getName(), p.getCategory(), p.getUnitPrice(),
                p.getVatRate(), p.isArchived());
    }

    private QuoteDto toDto(Quote q, AuthUser me) {
        LocalDate today = today();
        QuoteStatus status = q.effectiveStatus(today);
        boolean canEdit = status == QuoteStatus.DRAFT && (me.isAdmin() || q.getSalesId().equals(me.id()));
        boolean canDecide = status == QuoteStatus.SENT && q.getClientId().equals(me.id());
        List<LineDto> lines = q.getLines().stream().map(l -> new LineDto(l.getId(), l.getProduct().getId(),
                l.effectiveReference(), l.effectiveLabel(), l.getQuantity(), l.effectiveUnitPrice(), l.getDiscount(),
                l.effectiveVatRate(), lineTotal(l), l.isFrozen())).collect(Collectors.toList());
        return new QuoteDto(q.getId(), q.getNumber(), q.getTitle(), status, q.getSalesId(),
                userLookup.name(q.getSalesId()), q.getClientId(), userLookup.name(q.getClientId()), q.getValidUntil(),
                q.getSentAt(), q.getDecidedAt(), q.getRefusalComment(), lines, totals(q), canEdit, canDecide,
                q.getCreatedAt(), q.getUpdatedAt());
    }
}
