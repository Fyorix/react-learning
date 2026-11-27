package fr.epita.platform.apps.grouporder.service;

import fr.epita.platform.apps.grouporder.dto.GroupOrderDtos.*;
import fr.epita.platform.apps.grouporder.entity.*;
import fr.epita.platform.apps.grouporder.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupOrderService {

    private static final Duration RECENT = Duration.ofDays(30);

    private final SupplierRepository suppliers;
    private final MenuItemRepository items;
    private final ItemOptionRepository options;
    private final GroupOrderRepository orders;
    private final OrderLineRepository lines;
    private final PaymentRepository payments;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- fournisseurs ----------

    @Transactional(readOnly = true)
    public List<SupplierDto> suppliers() {
        return suppliers.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional
    public SupplierDto createSupplier(AuthUser me, SupplierRequest req) {
        me.requireAdmin();
        if (suppliers.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("SUPPLIER_EXISTS", "Ce fournisseur existe déjà");
        }
        Supplier s = new Supplier();
        s.setName(req.name().trim());
        s.setMinimumOrder(req.minimumOrder());
        s.setDeliveryFee(req.deliveryFee());
        return toDto(suppliers.saveAndFlush(s));
    }

    /** Remplace la carte : articles absents archivés, options absentes supprimées (les lignes gardent leur libellé figé). */
    @Transactional
    public SupplierDto replaceMenu(AuthUser me, UUID supplierId, MenuRequest req) {
        me.requireAdmin();
        Supplier s = suppliers.findById(supplierId).orElseThrow(() -> ApiException.notFound("Fournisseur"));
        Map<UUID, MenuItem> existing = items.findBySupplierIdOrderByPositionAsc(supplierId).stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));
        Set<UUID> kept = new HashSet<>();
        int position = 0;
        for (int i = 0; i < req.items().size(); i++) {
            MenuItemRequest r = req.items().get(i);
            MenuItem item;
            if (r.id() != null) {
                item = existing.get(r.id());
                if (item == null) {
                    throw ValidationException.field("items[" + i + "].id", "n'appartient pas à la carte de ce fournisseur");
                }
            } else {
                item = new MenuItem();
                item.setSupplierId(supplierId);
            }
            item.setName(r.name().trim());
            item.setPrice(r.price());
            item.setAvailable(r.available());
            item.setArchived(false);
            item.setPosition(position++);
            item = items.save(item);
            kept.add(item.getId());
            replaceOptions(item.getId(), r.options() == null ? List.of() : r.options(), "items[" + i + "]");
        }
        existing.values().stream().filter(it -> !kept.contains(it.getId())).forEach(it -> it.setArchived(true));
        items.flush();
        return toDto(s);
    }

    private void replaceOptions(UUID itemId, List<OptionRequest> reqs, String path) {
        Map<UUID, ItemOption> existing = options.findByItemId(itemId).stream().collect(Collectors.toMap(ItemOption::getId, Function.identity()));
        Set<UUID> kept = new HashSet<>();
        for (int j = 0; j < reqs.size(); j++) {
            OptionRequest r = reqs.get(j);
            ItemOption o = r.id() != null ? existing.get(r.id()) : new ItemOption();
            if (o == null) {
                throw ValidationException.field(path + ".options[" + j + "].id", "n'appartient pas à cet article");
            }
            o.setItemId(itemId);
            o.setLabel(r.label().trim());
            o.setExtraPrice(r.extraPrice());
            kept.add(options.save(o).getId());
        }
        existing.values().stream().filter(o -> !kept.contains(o.getId())).forEach(options::delete);
    }

    // ---------- commandes ----------

    @Transactional
    public OrderDto open(AuthUser me, OrderRequest req) {
        suppliers.findById(req.supplierId()).orElseThrow(() -> ApiException.notFound("Fournisseur"));
        if (!req.deadline().isAfter(clock.instant())) {
            throw ValidationException.field("deadline", "doit être dans le futur");
        }
        GroupOrder o = new GroupOrder();
        o.setSupplierId(req.supplierId());
        o.setOrganizerId(me.id());
        o.setDeadline(req.deadline());
        o.setNote(req.note() == null || req.note().isBlank() ? null : req.note().trim());
        o.setStatus(OrderStatus.OPEN);
        return detail(me, orders.saveAndFlush(o));
    }

    /** Sans filtre : commandes ouvertes + celles des 30 derniers jours. */
    @Transactional(readOnly = true)
    public List<OrderSummaryDto> list(OrderStatus status) {
        var since = clock.instant().minus(RECENT);
        Map<UUID, String> supplierNames = suppliers.findAll().stream().collect(Collectors.toMap(Supplier::getId, Supplier::getName));
        return orders.findAllByOrderByDeadlineDesc().stream()
                .filter(o -> status != null ? o.getStatus() == status : o.getStatus() == OrderStatus.OPEN || o.getDeadline().isAfter(since))
                .map(o -> {
                    Bill bill = bill(o);
                    return new OrderSummaryDto(o.getId(), supplierNames.get(o.getSupplierId()), o.getOrganizerId(),
                            userLookup.name(o.getOrganizerId()), o.getDeadline(), o.getStatus(), o.getNote(), bill.participants(), bill.total());
                }).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto get(AuthUser me, UUID id) {
        return detail(me, find(id));
    }

    /** Remplace mes lignes, sous verrou de la commande (la clôture ne peut pas se glisser entre-temps). */
    @Transactional
    public OrderDto replaceMyLines(AuthUser me, UUID id, LinesRequest req) {
        GroupOrder o = orders.lock(id).orElseThrow(() -> ApiException.notFound("Commande"));
        requireEditable(o);
        Map<UUID, MenuItem> menu = items.findBySupplierIdOrderByPositionAsc(o.getSupplierId()).stream()
                .collect(Collectors.toMap(MenuItem::getId, Function.identity()));
        Map<UUID, ItemOption> opts = options.findByItemIdInOrderByLabelAsc(menu.keySet()).stream()
                .collect(Collectors.toMap(ItemOption::getId, Function.identity()));
        List<OrderLine> fresh = new ArrayList<>();
        for (int i = 0; i < req.lines().size(); i++) {
            LineRequest r = req.lines().get(i);
            MenuItem item = menu.get(r.itemId());
            if (item == null) {
                throw ValidationException.field("lines[" + i + "].itemId", "ne fait pas partie de la carte de ce fournisseur");
            }
            if (!item.isAvailable() || item.isArchived()) {
                throw ApiException.conflict("ITEM_UNAVAILABLE", "« " + item.getName() + " » n'est pas disponible");
            }
            List<UUID> chosen = r.optionIds() == null ? List.of() : r.optionIds().stream().distinct().toList();
            List<ItemOption> picked = new ArrayList<>();
            for (UUID optId : chosen) {
                ItemOption opt = opts.get(optId);
                if (opt == null || !opt.getItemId().equals(item.getId())) {
                    throw ValidationException.field("lines[" + i + "].optionIds", "option inconnue pour cet article");
                }
                picked.add(opt);
            }
            OrderLine l = new OrderLine();
            l.setOrderId(id);
            l.setUserId(me.id());
            l.setItemId(item.getId());
            l.setOptionIds(new ArrayList<>(chosen));
            l.setQuantity(r.quantity());
            l.setUnitPrice(picked.stream().map(ItemOption::getExtraPrice).reduce(item.getPrice(), BigDecimal::add));
            l.setLabel(picked.isEmpty() ? item.getName()
                    : item.getName() + " (" + picked.stream().map(ItemOption::getLabel).collect(Collectors.joining(", ")) + ")");
            fresh.add(l);
        }
        lines.deleteAll(lines.findByOrderIdAndUserId(id, me.id()));
        lines.flush();
        lines.saveAllAndFlush(fresh);
        return detail(me, o);
    }

    @Transactional
    public OrderDto close(AuthUser me, UUID id) {
        GroupOrder o = organizerLock(me, id);
        transition(o, OrderStatus.OPEN);
        Bill bill = bill(o);
        Supplier s = suppliers.findById(o.getSupplierId()).orElseThrow();
        if (bill.subtotal().compareTo(s.getMinimumOrder()) < 0) {
            throw ApiException.conflict("MINIMUM_NOT_REACHED", "Minimum de commande non atteint : " + bill.subtotal() + " € sur " + s.getMinimumOrder() + " €");
        }
        o.setStatus(OrderStatus.CLOSED);
        bill.persons().keySet().forEach(userId -> {
            Payment p = new Payment();
            p.setId(new Payment.Key(id, userId));
            p.setPaid(userId.equals(o.getOrganizerId()));
            payments.save(p);
        });
        payments.flush();
        return detail(me, orders.saveAndFlush(o));
    }

    @Transactional
    public OrderDto cancel(AuthUser me, UUID id) {
        GroupOrder o = organizerLock(me, id);
        transition(o, OrderStatus.OPEN, OrderStatus.CLOSED);
        o.setStatus(OrderStatus.CANCELLED);
        return detail(me, orders.saveAndFlush(o));
    }

    @Transactional
    public OrderDto delivered(AuthUser me, UUID id) {
        GroupOrder o = organizerLock(me, id);
        transition(o, OrderStatus.CLOSED);
        o.setStatus(OrderStatus.DELIVERED);
        return detail(me, orders.saveAndFlush(o));
    }

    @Transactional
    public OrderDto setPaid(AuthUser me, UUID id, UUID userId, boolean paid) {
        GroupOrder o = organizerLock(me, id);
        transition(o, OrderStatus.CLOSED, OrderStatus.DELIVERED);
        Payment p = payments.findById(new Payment.Key(id, userId)).orElseThrow(() -> ApiException.notFound("Participant"));
        p.setPaid(paid);
        payments.saveAndFlush(p);
        return detail(me, o);
    }

    @Transactional(readOnly = true)
    public SummaryDto summary(UUID id) {
        GroupOrder o = find(id);
        Supplier s = suppliers.findById(o.getSupplierId()).orElseThrow();
        Map<String, int[]> qty = new TreeMap<>();
        Map<String, BigDecimal> amounts = new HashMap<>();
        for (OrderLine l : lines.findByOrderIdOrderByCreatedAtAsc(id)) {
            qty.computeIfAbsent(l.getLabel(), k -> new int[1])[0] += l.getQuantity();
            amounts.merge(l.getLabel(), amount(l), BigDecimal::add);
        }
        List<SummaryRow> rows = qty.entrySet().stream().map(e -> new SummaryRow(e.getKey(), e.getValue()[0], amounts.get(e.getKey()))).toList();
        Bill bill = bill(o);
        return new SummaryDto(id, s.getName(), rows, bill.subtotal(), bill.fee(), bill.total());
    }

    // ---------- calcul ----------

    /**
     * Répartition : chaque participant (au moins une ligne) paie ses lignes + fee / n arrondi au centime inférieur ;
     * le reste d'arrondi revient à l'organisateur (ajouté à sa part, même s'il n'a rien commandé).
     */
    record Bill(Map<UUID, List<OrderLine>> persons, Map<UUID, BigDecimal> shares, BigDecimal subtotal, BigDecimal fee,
                BigDecimal total, int participants) {
    }

    Bill bill(GroupOrder o) {
        Supplier s = suppliers.findById(o.getSupplierId()).orElseThrow();
        Map<UUID, List<OrderLine>> persons = lines.findByOrderIdOrderByCreatedAtAsc(o.getId()).stream()
                .collect(Collectors.groupingBy(OrderLine::getUserId, LinkedHashMap::new, Collectors.toList()));
        BigDecimal subtotal = persons.values().stream().flatMap(List::stream).map(this::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<UUID, BigDecimal> shares = new LinkedHashMap<>();
        BigDecimal fee = persons.isEmpty() ? BigDecimal.ZERO.setScale(2) : s.getDeliveryFee();
        if (!persons.isEmpty()) {
            BigDecimal share = fee.divide(BigDecimal.valueOf(persons.size()), 2, RoundingMode.DOWN);
            persons.keySet().forEach(u -> shares.put(u, share));
            BigDecimal rest = fee.subtract(share.multiply(BigDecimal.valueOf(persons.size())));
            if (rest.signum() > 0) {
                shares.merge(o.getOrganizerId(), rest, BigDecimal::add);
                persons.putIfAbsent(o.getOrganizerId(), List.of());
            }
        }
        long participants = persons.values().stream().filter(l -> !l.isEmpty()).count();
        return new Bill(persons, shares, subtotal, fee, subtotal.add(fee), (int) participants);
    }

    private BigDecimal amount(OrderLine l) {
        return l.getUnitPrice().multiply(BigDecimal.valueOf(l.getQuantity()));
    }

    private OrderDto detail(AuthUser me, GroupOrder o) {
        Supplier s = suppliers.findById(o.getSupplierId()).orElseThrow();
        Bill bill = bill(o);
        Map<UUID, Boolean> paid = payments.findByIdOrderId(o.getId()).stream()
                .collect(Collectors.toMap(p -> p.getId().getUserId(), Payment::isPaid));
        Map<UUID, String> names = userLookup.names(bill.persons().keySet());
        List<PersonDto> persons = bill.persons().entrySet().stream().map(e -> {
            List<LineDto> ls = e.getValue().stream().map(this::toDto).toList();
            BigDecimal linesTotal = ls.stream().map(LineDto::amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
            BigDecimal share = bill.shares().getOrDefault(e.getKey(), BigDecimal.ZERO.setScale(2));
            return new PersonDto(e.getKey(), names.get(e.getKey()), ls, linesTotal, share, linesTotal.add(share),
                    o.getStatus() == OrderStatus.OPEN ? null : paid.getOrDefault(e.getKey(), false));
        }).sorted(Comparator.comparing(PersonDto::name, Comparator.nullsLast(String::compareToIgnoreCase))).toList();
        List<LineDto> mine = bill.persons().getOrDefault(me.id(), List.of()).stream().map(this::toDto).toList();
        return new OrderDto(o.getId(), s.getId(), s.getName(), s.getMinimumOrder(), s.getDeliveryFee(), o.getOrganizerId(),
                userLookup.name(o.getOrganizerId()), o.getDeadline(), o.getNote(), o.getStatus(), editable(o),
                o.getOrganizerId().equals(me.id()), bill.subtotal(), bill.total(),
                bill.subtotal().compareTo(s.getMinimumOrder()) >= 0, persons, mine);
    }

    private LineDto toDto(OrderLine l) {
        return new LineDto(l.getItemId(), l.getLabel(), List.copyOf(l.getOptionIds()), l.getUnitPrice(), l.getQuantity(), amount(l));
    }

    private SupplierDto toDto(Supplier s) {
        List<MenuItem> menu = items.findBySupplierIdAndArchivedFalseOrderByPositionAsc(s.getId());
        Map<UUID, List<OptionDto>> opts = options.findByItemIdInOrderByLabelAsc(menu.stream().map(MenuItem::getId).toList()).stream()
                .collect(Collectors.groupingBy(ItemOption::getItemId, Collectors.mapping(o -> new OptionDto(o.getId(), o.getLabel(), o.getExtraPrice()), Collectors.toList())));
        return new SupplierDto(s.getId(), s.getName(), s.getMinimumOrder(), s.getDeliveryFee(),
                menu.stream().map(i -> new ItemDto(i.getId(), i.getName(), i.getPrice(), i.isAvailable(), opts.getOrDefault(i.getId(), List.of()))).toList());
    }

    // ---------- garde-fous ----------

    private boolean editable(GroupOrder o) {
        return o.getStatus() == OrderStatus.OPEN && clock.instant().isBefore(o.getDeadline());
    }

    private void requireEditable(GroupOrder o) {
        if (!editable(o)) {
            throw ApiException.conflict("ORDER_CLOSED", "La commande n'accepte plus de modification (heure limite passée ou commande clôturée)");
        }
    }

    private GroupOrder find(UUID id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("Commande"));
    }

    private GroupOrder organizerLock(AuthUser me, UUID id) {
        GroupOrder o = orders.lock(id).orElseThrow(() -> ApiException.notFound("Commande"));
        if (!o.getOrganizerId().equals(me.id())) {
            throw ApiException.forbidden("Action réservée à l'organisateur");
        }
        return o;
    }

    private void transition(GroupOrder o, OrderStatus... allowed) {
        if (!Arrays.asList(allowed).contains(o.getStatus())) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Action impossible pour une commande " + o.getStatus());
        }
    }
}
