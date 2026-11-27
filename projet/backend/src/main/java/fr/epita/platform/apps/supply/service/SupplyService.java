package fr.epita.platform.apps.supply.service;

import fr.epita.platform.apps.supply.dto.SupplyDtos.*;
import fr.epita.platform.apps.supply.entity.*;
import fr.epita.platform.apps.supply.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Stock : `reserved` augmente à la validation, `stock` et `reserved` baissent à la remise.
 * Quota mensuel : somme des quantités demandées dans le mois (demandes en attente, validées ou remises).
 */
@Service
@RequiredArgsConstructor
public class SupplyService {

    public static final String REPORTS = "supply_reports";
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final Set<RequestStatus> COUNTED = EnumSet.of(RequestStatus.PENDING, RequestStatus.APPROVED, RequestStatus.DELIVERED);

    private final SupplyItemRepository items;
    private final SupplyRequestRepository requests;
    private final SupplyLineRepository lines;
    private final RestockRepository restocks;
    private final ManagerRelations relations;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- catalogue ----------

    @Transactional(readOnly = true)
    public List<ItemDto> catalog(AuthUser me, String category, String search) {
        Map<UUID, Integer> used = usedThisMonth(me.id(), null);
        String q = search == null ? null : search.trim().toLowerCase();
        return items.findAllByOrderByCategoryAscNameAsc().stream()
                .filter(i -> me.isAdmin() || i.isActive())
                .filter(i -> category == null || category.isBlank() || i.getCategory().equalsIgnoreCase(category))
                .filter(i -> q == null || q.isEmpty() || i.getName().toLowerCase().contains(q) || i.getCategory().toLowerCase().contains(q))
                .map(i -> toDto(i, used)).toList();
    }

    @Transactional
    public ItemDto create(AuthUser me, ItemRequest req) {
        me.requireAdmin();
        if (items.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("ITEM_EXISTS", "Un article porte déjà ce nom");
        }
        SupplyItem i = new SupplyItem();
        i.setStock(req.stock());
        apply(i, req);
        return toDto(items.saveAndFlush(i), Map.of());
    }

    @Transactional
    public ItemDto update(AuthUser me, UUID id, ItemRequest req) {
        me.requireAdmin();
        SupplyItem i = items.lock(List.of(id)).stream().findFirst().orElseThrow(() -> ApiException.notFound("Article"));
        if (!i.getName().equalsIgnoreCase(req.name().trim()) && items.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("ITEM_EXISTS", "Un article porte déjà ce nom");
        }
        apply(i, req);
        return toDto(items.saveAndFlush(i), Map.of());
    }

    private void apply(SupplyItem i, ItemRequest req) {
        i.setName(req.name().trim());
        i.setCategory(req.category().trim());
        i.setUnit(req.unit().trim());
        i.setReorderThreshold(req.reorderThreshold());
        i.setMonthlyQuota(req.monthlyQuota());
        i.setActive(req.active() == null || req.active());
    }

    @Transactional
    public ItemDto restock(AuthUser me, UUID id, int quantity) {
        me.requireAdmin();
        SupplyItem i = items.lock(List.of(id)).stream().findFirst().orElseThrow(() -> ApiException.notFound("Article"));
        i.setStock(i.getStock() + quantity);
        Restock r = new Restock();
        r.setItemId(id);
        r.setQuantity(quantity);
        r.setCreatedBy(me.id());
        restocks.save(r);
        return toDto(items.saveAndFlush(i), Map.of());
    }

    @Transactional(readOnly = true)
    public List<ItemDto> alerts(AuthUser me) {
        me.requireAdmin();
        return items.findAllByOrderByCategoryAscNameAsc().stream().filter(i -> i.isActive() && i.available() < i.getReorderThreshold())
                .map(i -> toDto(i, Map.of())).toList();
    }

    // ---------- demandes ----------

    @Transactional
    public RequestDto request(AuthUser me, SupplyRequestBody body) {
        Map<UUID, Integer> wanted = new LinkedHashMap<>();
        body.lines().forEach(l -> wanted.merge(l.itemId(), l.quantity(), Integer::sum));
        List<SupplyItem> locked = items.lock(wanted.keySet());
        Map<UUID, SupplyItem> byId = locked.stream().collect(Collectors.toMap(SupplyItem::getId, Function.identity()));
        for (UUID id : wanted.keySet()) {
            SupplyItem i = byId.get(id);
            if (i == null || !i.isActive()) {
                throw ApiException.notFound("Article");
            }
        }
        checkStockAndQuota(me.id(), wanted, byId, null);
        SupplyRequest r = new SupplyRequest();
        r.setUserId(me.id());
        r.setStatus(RequestStatus.PENDING);
        requests.saveAndFlush(r);
        wanted.forEach((itemId, q) -> {
            SupplyLine l = new SupplyLine();
            l.setRequestId(r.getId());
            l.setItemId(itemId);
            l.setQuantity(q);
            lines.save(l);
        });
        lines.flush();
        return toDto(r);
    }

    @Transactional(readOnly = true)
    public List<RequestDto> mine(AuthUser me) {
        return requests.findByUserIdOrderByCreatedAtDesc(me.id()).stream().map(this::toDto).toList();
    }

    @Transactional
    public void cancel(AuthUser me, UUID id) {
        SupplyRequest r = requests.lock(id).orElseThrow(() -> ApiException.notFound("Demande"));
        if (!r.getUserId().equals(me.id())) {
            throw ApiException.notFound("Demande");
        }
        if (r.getStatus() == RequestStatus.DELIVERED) {
            throw ApiException.conflict("REQUEST_DELIVERED", "La demande a déjà été remise");
        }
        transition(r, RequestStatus.PENDING, RequestStatus.APPROVED);
        if (r.getStatus() == RequestStatus.APPROVED) {
            release(r);
        }
        r.setStatus(RequestStatus.CANCELLED);
        requests.saveAndFlush(r);
    }

    @Transactional(readOnly = true)
    public List<RequestDto> adminList(AuthUser me, RequestStatus status) {
        me.requireAdmin();
        return (status == null ? requests.findAllByOrderByCreatedAtAsc() : requests.findByStatusOrderByCreatedAtAsc(status))
                .stream().map(this::toDto).toList();
    }

    /** Validation : stock disponible et quota revérifiés sous verrou, puis réservation. */
    @Transactional
    public RequestDto approve(AuthUser me, UUID id) {
        me.requireAdmin();
        SupplyRequest r = requests.lock(id).orElseThrow(() -> ApiException.notFound("Demande"));
        transition(r, RequestStatus.PENDING);
        Map<UUID, Integer> wanted = quantities(r);
        Map<UUID, SupplyItem> byId = items.lock(wanted.keySet()).stream().collect(Collectors.toMap(SupplyItem::getId, Function.identity()));
        checkStockAndQuota(r.getUserId(), wanted, byId, r.getId());
        wanted.forEach((itemId, q) -> byId.get(itemId).setReserved(byId.get(itemId).getReserved() + q));
        items.saveAll(byId.values());
        r.setStatus(RequestStatus.APPROVED);
        r.setDecidedBy(me.id());
        items.flush();
        return toDto(requests.saveAndFlush(r));
    }

    @Transactional
    public RequestDto reject(AuthUser me, UUID id, String reason) {
        me.requireAdmin();
        SupplyRequest r = requests.lock(id).orElseThrow(() -> ApiException.notFound("Demande"));
        transition(r, RequestStatus.PENDING, RequestStatus.APPROVED);
        if (r.getStatus() == RequestStatus.APPROVED) {
            release(r);
        }
        r.setStatus(RequestStatus.REJECTED);
        r.setReason(reason.trim());
        r.setDecidedBy(me.id());
        return toDto(requests.saveAndFlush(r));
    }

    @Transactional
    public RequestDto deliver(AuthUser me, UUID id) {
        me.requireAdmin();
        SupplyRequest r = requests.lock(id).orElseThrow(() -> ApiException.notFound("Demande"));
        transition(r, RequestStatus.APPROVED);
        Map<UUID, Integer> wanted = quantities(r);
        items.lock(wanted.keySet()).forEach(i -> {
            int q = wanted.get(i.getId());
            i.setReserved(i.getReserved() - q);
            i.setStock(i.getStock() - q);
            items.save(i);
        });
        r.setStatus(RequestStatus.DELIVERED);
        r.setDeliveredAt(clock.instant());
        items.flush();
        return toDto(requests.saveAndFlush(r));
    }

    @Transactional(readOnly = true)
    public ConsumptionDto consumption(AuthUser me, LocalDate from, LocalDate to) {
        me.requireAdmin();
        LocalDate start = from == null ? LocalDate.now(clock).withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        if (end.isBefore(start)) {
            throw ValidationException.field("to", "doit être postérieure à from");
        }
        List<SupplyRequest> delivered = requests.findByStatusAndDeliveredAtBetween(RequestStatus.DELIVERED,
                start.atStartOfDay(ZONE).toInstant(), end.plusDays(1).atStartOfDay(ZONE).toInstant());
        Map<UUID, SupplyRequest> byId = delivered.stream().collect(Collectors.toMap(SupplyRequest::getId, Function.identity()));
        List<SupplyLine> ls = byId.isEmpty() ? List.of() : lines.findByRequestIdIn(byId.keySet());
        Map<UUID, String> itemNames = items.findAll().stream().collect(Collectors.toMap(SupplyItem::getId, SupplyItem::getName));
        Map<String, Long> byItem = new TreeMap<>();
        Map<String, Long> byTeam = new TreeMap<>();
        for (SupplyLine l : ls) {
            byItem.merge(itemNames.get(l.getItemId()), (long) l.getQuantity(), Long::sum);
            byTeam.merge(teamName(byId.get(l.getRequestId()).getUserId()), (long) l.getQuantity(), Long::sum);
        }
        return new ConsumptionDto(rows(byItem), rows(byTeam));
    }

    // ---------- règles ----------

    private void checkStockAndQuota(UUID userId, Map<UUID, Integer> wanted, Map<UUID, SupplyItem> byId, UUID excludeRequest) {
        Map<UUID, Integer> used = usedThisMonth(userId, excludeRequest);
        for (var e : wanted.entrySet()) {
            SupplyItem i = byId.get(e.getKey());
            if (e.getValue() > i.available()) {
                throw ApiException.conflict("INSUFFICIENT_STOCK", "Stock insuffisant pour « " + i.getName() + " » : " + i.available() + " " + i.getUnit() + " disponible(s)");
            }
            if (i.getMonthlyQuota() != null && used.getOrDefault(i.getId(), 0) + e.getValue() > i.getMonthlyQuota()) {
                throw ApiException.conflict("QUOTA_EXCEEDED", "Quota mensuel dépassé pour « " + i.getName() + " » : "
                        + Math.max(0, i.getMonthlyQuota() - used.getOrDefault(i.getId(), 0)) + " restant(s) ce mois-ci");
            }
        }
    }

    /** Quantités déjà demandées ce mois-ci (hors une demande donnée) par article. */
    private Map<UUID, Integer> usedThisMonth(UUID userId, UUID excludeRequest) {
        YearMonth m = YearMonth.now(clock.withZone(ZONE));
        Instant from = m.atDay(1).atStartOfDay(ZONE).toInstant();
        Instant to = m.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        List<UUID> ids = requests.findByUserIdAndStatusInAndCreatedAtBetween(userId, COUNTED, from, to).stream()
                .map(SupplyRequest::getId).filter(id -> !id.equals(excludeRequest)).toList();
        Map<UUID, Integer> used = new HashMap<>();
        if (!ids.isEmpty()) {
            lines.findByRequestIdIn(ids).forEach(l -> used.merge(l.getItemId(), l.getQuantity(), Integer::sum));
        }
        return used;
    }

    private void release(SupplyRequest r) {
        Map<UUID, Integer> wanted = quantities(r);
        items.lock(wanted.keySet()).forEach(i -> {
            i.setReserved(i.getReserved() - wanted.get(i.getId()));
            items.save(i);
        });
        items.flush();
    }

    private Map<UUID, Integer> quantities(SupplyRequest r) {
        return lines.findByRequestIdOrderByCreatedAtAsc(r.getId()).stream()
                .collect(Collectors.toMap(SupplyLine::getItemId, SupplyLine::getQuantity, Integer::sum, LinkedHashMap::new));
    }

    private void transition(SupplyRequest r, RequestStatus... allowed) {
        if (!Arrays.asList(allowed).contains(r.getStatus())) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Action impossible pour une demande " + r.getStatus());
        }
    }

    private String teamName(UUID userId) {
        return relations.managerOf(REPORTS, userId).map(m -> "Équipe de " + userLookup.name(m)).orElse("Sans équipe");
    }

    private static List<ConsumptionRow> rows(Map<String, Long> m) {
        return m.entrySet().stream().map(e -> new ConsumptionRow(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(ConsumptionRow::quantity).reversed()).toList();
    }

    private ItemDto toDto(SupplyItem i, Map<UUID, Integer> used) {
        Integer remaining = i.getMonthlyQuota() == null ? null : Math.max(0, i.getMonthlyQuota() - used.getOrDefault(i.getId(), 0));
        return new ItemDto(i.getId(), i.getName(), i.getCategory(), i.getUnit(), i.getStock(), i.getReserved(), i.available(),
                i.getReorderThreshold(), i.getMonthlyQuota(), i.isActive(), i.available() < i.getReorderThreshold(), remaining);
    }

    private RequestDto toDto(SupplyRequest r) {
        Map<UUID, SupplyItem> byId = new HashMap<>();
        List<LineDto> ls = lines.findByRequestIdOrderByCreatedAtAsc(r.getId()).stream().map(l -> {
            SupplyItem i = byId.computeIfAbsent(l.getItemId(), k -> items.findById(k).orElseThrow());
            return new LineDto(i.getId(), i.getName(), i.getUnit(), l.getQuantity());
        }).toList();
        return new RequestDto(r.getId(), r.getUserId(), userLookup.name(r.getUserId()), teamName(r.getUserId()), r.getStatus(), r.getReason(),
                r.getDecidedBy() == null ? null : userLookup.name(r.getDecidedBy()), r.getCreatedAt(), r.getDeliveredAt(), ls);
    }
}
