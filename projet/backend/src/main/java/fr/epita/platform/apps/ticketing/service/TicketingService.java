package fr.epita.platform.apps.ticketing.service;

import fr.epita.platform.apps.ticketing.dto.TicketingDtos.*;
import fr.epita.platform.apps.ticketing.entity.*;
import fr.epita.platform.apps.ticketing.repository.EventTicketRepository;
import fr.epita.platform.apps.ticketing.repository.TicketOrderRepository;
import fr.epita.platform.apps.ticketing.repository.TicketRateRepository;
import fr.epita.platform.apps.ticketing.repository.TicketedEventRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

/**
 * Ventes sous verrou de l'événement : ni le quota d'un tarif ni la jauge ne sont dépassés, même en achats simultanés.
 * Billet = UUID aléatoire + somme de contrôle CRC32 ; contrôle unique à l'entrée (verrou sur le billet).
 */
@Service
@RequiredArgsConstructor
public class TicketingService {

    public static final String ROLES = "ticketing_roles";
    public static final String SCANNER = "SCANNER";
    static final int ORDER_LIMIT = 6;
    static final Duration REFUND_LIMIT = Duration.ofHours(48);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Paris"));

    private final TicketedEventRepository events;
    private final TicketRateRepository rates;
    private final TicketOrderRepository orders;
    private final EventTicketRepository tickets;
    private final AppRoles roles;
    private final UserLookup userLookup;
    private final Clock clock;

    /** Code de billet : UUID + 8 caractères hexadécimaux de CRC32 (détecte les fautes de saisie). */
    public static String newCode() {
        String id = UUID.randomUUID().toString();
        return id + "-" + checksum(id);
    }

    static String checksum(String id) {
        CRC32 crc = new CRC32();
        crc.update(id.getBytes(StandardCharsets.US_ASCII));
        return "%08X".formatted(crc.getValue());
    }

    static boolean wellFormed(String code) {
        int i = code.lastIndexOf('-');
        return i == 36 && code.length() == 45 && checksum(code.substring(0, 36)).equalsIgnoreCase(code.substring(37));
    }

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(SCANNER));
    }

    // ---------- événements ----------

    @Transactional(readOnly = true)
    public List<EventDto> list(AuthUser me, Boolean upcoming) {
        List<TicketedEvent> list = Boolean.FALSE.equals(upcoming) ? events.findAllByOrderByStartsAtDesc() : events.findByStartsAtAfterOrderByStartsAtAsc(clock.instant());
        return list.stream().map(e -> toDto(me, e)).toList();
    }

    @Transactional(readOnly = true)
    public EventDto get(AuthUser me, UUID id) {
        return toDto(me, event(id));
    }

    @Transactional
    public EventDto create(AuthUser me, EventRequest req) {
        validate(req);
        if (!req.startsAt().isAfter(clock.instant())) {
            throw ValidationException.field("startsAt", "doit être dans le futur");
        }
        TicketedEvent e = new TicketedEvent();
        e.setOrganizerId(me.id());
        apply(e, req);
        events.saveAndFlush(e);
        for (int i = 0; i < req.rates().size(); i++) {
            rates.save(newRate(e.getId(), i + 1, req.rates().get(i)));
        }
        rates.flush();
        return toDto(me, e);
    }

    /** Modification : quotas et jauge jamais sous le vendu, tarif vendu jamais supprimé. */
    @Transactional
    public EventDto update(AuthUser me, UUID id, EventRequest req) {
        TicketedEvent e = events.lock(id).orElseThrow(() -> ApiException.notFound("Événement"));
        organizer(me, e);
        validate(req);
        long sold = tickets.countByEventIdAndStatus(id, EventTicketStatus.VALID);
        if (req.capacity() < sold) {
            throw ApiException.conflict("QUOTA_BELOW_SOLD", "Jauge inférieure aux " + sold + " billets vendus");
        }
        Map<UUID, TicketRate> existing = rates.findByEventIdOrderByPositionAsc(id).stream().collect(Collectors.toMap(TicketRate::getId, r -> r));
        Set<UUID> kept = new HashSet<>();
        for (int i = 0; i < req.rates().size(); i++) {
            RateInput in = req.rates().get(i);
            if (in.id() == null) {
                rates.save(newRate(id, i + 1, in));
                continue;
            }
            TicketRate r = existing.get(in.id());
            if (r == null) {
                throw ValidationException.field("rates[" + i + "].id", "tarif étranger à l'événement");
            }
            long rateSold = tickets.countByRateIdAndStatus(r.getId(), EventTicketStatus.VALID);
            if (in.quota() < rateSold) {
                throw ApiException.conflict("QUOTA_BELOW_SOLD", "Quota du tarif " + r.getLabel() + " inférieur aux " + rateSold + " billets vendus");
            }
            r.setPosition(i + 1);
            r.setLabel(in.label().trim());
            r.setPrice(in.price());
            r.setQuota(in.quota());
            rates.save(r);
            kept.add(r.getId());
        }
        for (TicketRate r : existing.values()) {
            if (!kept.contains(r.getId())) {
                if (tickets.countByRateIdAndStatus(r.getId(), EventTicketStatus.VALID) > 0) {
                    throw ApiException.conflict("QUOTA_BELOW_SOLD", "Le tarif " + r.getLabel() + " a des billets vendus : il ne peut pas être supprimé");
                }
                rates.delete(r);
            }
        }
        apply(e, req);
        events.saveAndFlush(e);
        rates.flush();
        return toDto(me, e);
    }

    private void validate(EventRequest req) {
        int quotas = req.rates().stream().mapToInt(RateInput::quota).sum();
        var errors = new ValidationException.Collector();
        errors.check(quotas <= req.capacity(), "rates", "la somme des quotas (" + quotas + ") dépasse la capacité (" + req.capacity() + ")");
        errors.check(!req.salesEndAt().isAfter(req.startsAt()), "salesEndAt", "au plus tard au début de l'événement");
        errors.throwIfAny();
    }

    private void apply(TicketedEvent e, EventRequest req) {
        e.setTitle(req.title().trim());
        e.setVenue(req.venue().trim());
        e.setStartsAt(req.startsAt());
        e.setCapacity(req.capacity());
        e.setSalesEndAt(req.salesEndAt());
    }

    private static TicketRate newRate(UUID eventId, int position, RateInput in) {
        TicketRate r = new TicketRate();
        r.setId(UUID.randomUUID());
        r.setEventId(eventId);
        r.setPosition(position);
        r.setLabel(in.label().trim());
        r.setPrice(in.price());
        r.setQuota(in.quota());
        return r;
    }

    // ---------- commandes ----------

    @Transactional
    public OrderDto order(AuthUser me, UUID eventId, List<OrderLine> lines) {
        TicketedEvent e = events.lock(eventId).orElseThrow(() -> ApiException.notFound("Événement"));
        if (!clock.instant().isBefore(e.getSalesEndAt())) {
            throw ApiException.conflict("SALES_CLOSED", "Les ventes sont closes");
        }
        int quantity = lines.stream().mapToInt(OrderLine::quantity).sum();
        if (quantity > ORDER_LIMIT) {
            throw ApiException.conflict("ORDER_LIMIT", ORDER_LIMIT + " billets maximum par commande");
        }
        Map<UUID, TicketRate> byId = rates.findByEventIdOrderByPositionAsc(eventId).stream().collect(Collectors.toMap(TicketRate::getId, r -> r));
        Map<UUID, Integer> wanted = new LinkedHashMap<>();
        for (int i = 0; i < lines.size(); i++) {
            if (!byId.containsKey(lines.get(i).rateId())) {
                throw ValidationException.field("lines[" + i + "].rateId", "tarif étranger à l'événement");
            }
            wanted.merge(lines.get(i).rateId(), lines.get(i).quantity(), Integer::sum);
        }
        long soldTotal = tickets.countByEventIdAndStatus(eventId, EventTicketStatus.VALID);
        if (soldTotal + quantity > e.getCapacity()) {
            throw ApiException.conflict("SOLD_OUT", "Plus que " + (e.getCapacity() - soldTotal) + " place(s) disponible(s)");
        }
        for (var w : wanted.entrySet()) {
            TicketRate r = byId.get(w.getKey());
            long left = r.getQuota() - tickets.countByRateIdAndStatus(r.getId(), EventTicketStatus.VALID);
            if (w.getValue() > left) {
                throw ApiException.conflict("SOLD_OUT", "Tarif " + r.getLabel() + " : plus que " + Math.max(0, left) + " place(s)");
            }
        }
        TicketOrder o = new TicketOrder();
        o.setEventId(eventId);
        o.setBuyerId(me.id());
        // paiement fictif : payée à la création
        o.setStatus(TicketOrderStatus.PAID);
        o.setTotal(wanted.entrySet().stream().map(w -> byId.get(w.getKey()).getPrice().multiply(BigDecimal.valueOf(w.getValue()))).reduce(BigDecimal.ZERO, BigDecimal::add));
        orders.saveAndFlush(o);
        wanted.forEach((rateId, n) -> {
            for (int i = 0; i < n; i++) {
                EventTicket t = new EventTicket();
                t.setId(UUID.randomUUID());
                t.setOrderId(o.getId());
                t.setEventId(eventId);
                t.setRateId(rateId);
                t.setCode(newCode());
                t.setStatus(EventTicketStatus.VALID);
                tickets.save(t);
            }
        });
        tickets.flush();
        return toDto(o);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> mine(AuthUser me) {
        return orders.findByBuyerIdOrderByCreatedAtDesc(me.id()).stream().map(this::toDto).toList();
    }

    /** Remboursement jusqu'à 48 h avant, sans billet scanné ; les places reviennent au quota. */
    @Transactional
    public OrderDto refund(AuthUser me, UUID id) {
        TicketOrder o = orders.lock(id).orElseThrow(() -> ApiException.notFound("Commande"));
        if (!o.getBuyerId().equals(me.id())) {
            throw ApiException.notFound("Commande");
        }
        if (o.getStatus() == TicketOrderStatus.REFUNDED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Commande déjà remboursée");
        }
        TicketedEvent e = events.lock(o.getEventId()).orElseThrow();
        if (clock.instant().isAfter(e.getStartsAt().minus(REFUND_LIMIT))) {
            throw ApiException.conflict("REFUND_CLOSED", "Remboursement impossible à moins de 48 h de l'événement");
        }
        List<EventTicket> ts = tickets.findByOrderId(id);
        if (ts.stream().anyMatch(t -> t.getScannedAt() != null)) {
            throw ApiException.conflict("TICKET_SCANNED", "Un billet de la commande a déjà été contrôlé");
        }
        ts.forEach(t -> t.setStatus(EventTicketStatus.REFUNDED));
        tickets.saveAll(ts);
        o.setStatus(TicketOrderStatus.REFUNDED);
        orders.saveAndFlush(o);
        return toDto(o);
    }

    // ---------- contrôle ----------

    @Transactional(noRollbackFor = ApiException.class)
    public ScanResult scan(AuthUser me, UUID eventId, String rawCode) {
        TicketedEvent e = event(eventId);
        if (!e.getOrganizerId().equals(me.id()) && !roles.has(ROLES, me, SCANNER)) {
            throw ApiException.forbidden("Réservé aux contrôleurs (SCANNER) et à l'organisateur");
        }
        String code = rawCode.trim();
        if (!wellFormed(code)) {
            throw ApiException.notFound("Billet");
        }
        EventTicket t = tickets.lockByCode(code.substring(0, 37) + code.substring(37).toUpperCase(Locale.ROOT)).orElseThrow(() -> ApiException.notFound("Billet"));
        if (!t.getEventId().equals(eventId)) {
            throw ApiException.conflict("WRONG_EVENT", "Billet d'un autre événement : " + event(t.getEventId()).getTitle());
        }
        if (t.getStatus() == EventTicketStatus.REFUNDED) {
            throw ApiException.conflict("TICKET_REFUNDED", "Billet remboursé");
        }
        if (t.getScannedAt() != null) {
            throw ApiException.conflict("ALREADY_SCANNED", "Billet déjà contrôlé à " + HOUR.format(t.getScannedAt()));
        }
        t.setScannedAt(clock.instant());
        t.setScannedBy(me.id());
        tickets.saveAndFlush(t);
        TicketOrder o = orders.findById(t.getOrderId()).orElseThrow();
        return new ScanResult(t.getCode(), userLookup.name(o.getBuyerId()), rates.findById(t.getRateId()).map(TicketRate::getLabel).orElse(null), t.getScannedAt());
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard(AuthUser me, UUID id) {
        TicketedEvent e = event(id);
        organizer(me, e);
        List<EventTicket> valid = tickets.findByEventIdAndStatus(id, EventTicketStatus.VALID);
        List<RateStats> stats = rates.findByEventIdOrderByPositionAsc(id).stream().map(r -> {
            List<EventTicket> mine = valid.stream().filter(t -> t.getRateId().equals(r.getId())).toList();
            return new RateStats(r.getId(), r.getLabel(), r.getQuota(), mine.size(), r.getPrice().multiply(BigDecimal.valueOf(mine.size())),
                    mine.stream().filter(t -> t.getScannedAt() != null).count());
        }).toList();
        long refunded = tickets.countByEventIdAndStatus(id, EventTicketStatus.REFUNDED) == 0 ? 0
                : tickets.findByEventIdAndStatus(id, EventTicketStatus.REFUNDED).stream().map(EventTicket::getOrderId).distinct().count();
        return new DashboardDto(id, e.getTitle(), e.getCapacity(), valid.size(), (int) (valid.size() * 100L / e.getCapacity()),
                stats.stream().map(RateStats::revenue).reduce(BigDecimal.ZERO, BigDecimal::add), valid.stream().filter(t -> t.getScannedAt() != null).count(), refunded, stats);
    }

    // ---------- outils ----------

    private TicketedEvent event(UUID id) {
        return events.findById(id).orElseThrow(() -> ApiException.notFound("Événement"));
    }

    private void organizer(AuthUser me, TicketedEvent e) {
        if (!me.isAdmin() && !e.getOrganizerId().equals(me.id())) {
            throw ApiException.forbidden("Réservé à l'organisateur");
        }
    }

    private EventDto toDto(AuthUser me, TicketedEvent e) {
        List<RateDto> rs = rates.findByEventIdOrderByPositionAsc(e.getId()).stream().map(r -> {
            long sold = tickets.countByRateIdAndStatus(r.getId(), EventTicketStatus.VALID);
            return new RateDto(r.getId(), r.getLabel(), r.getPrice(), r.getQuota(), sold, Math.max(0, r.getQuota() - sold));
        }).toList();
        long sold = tickets.countByEventIdAndStatus(e.getId(), EventTicketStatus.VALID);
        long remaining = Math.min(e.getCapacity() - sold, rs.stream().mapToLong(RateDto::remaining).sum());
        return new EventDto(e.getId(), e.getTitle(), e.getVenue(), e.getStartsAt(), e.getCapacity(), e.getSalesEndAt(), userLookup.name(e.getOrganizerId()),
                e.getOrganizerId().equals(me.id()), clock.instant().isBefore(e.getSalesEndAt()) && remaining > 0, sold, Math.max(0, remaining), rs);
    }

    private OrderDto toDto(TicketOrder o) {
        TicketedEvent e = event(o.getEventId());
        Map<UUID, TicketRate> byId = rates.findByEventIdOrderByPositionAsc(e.getId()).stream().collect(Collectors.toMap(TicketRate::getId, r -> r));
        List<EventTicket> ts = tickets.findByOrderId(o.getId());
        boolean refundable = o.getStatus() == TicketOrderStatus.PAID && !clock.instant().isAfter(e.getStartsAt().minus(REFUND_LIMIT))
                && ts.stream().noneMatch(t -> t.getScannedAt() != null);
        return new OrderDto(o.getId(), e.getId(), e.getTitle(), e.getStartsAt(), e.getVenue(), o.getStatus(), o.getTotal(), o.getCreatedAt(), refundable,
                ts.stream().map(t -> new TicketDto(t.getId(), t.getCode(), byId.get(t.getRateId()).getLabel(), byId.get(t.getRateId()).getPrice(), t.getStatus(), t.getScannedAt())).toList());
    }
}
