package fr.epita.platform.apps.license.service;

import fr.epita.platform.apps.license.dto.LicenseDtos.*;
import fr.epita.platform.apps.license.entity.*;
import fr.epita.platform.apps.license.repository.LicenseRequestRepository;
import fr.epita.platform.apps.license.repository.SeatRepository;
import fr.epita.platform.apps.license.repository.SoftwareRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import fr.epita.platform.user.entity.User;
import fr.epita.platform.user.service.UserDeletionGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Sièges attribués sous verrou du logiciel. Implémente {@link UserDeletionGuard} : à la désactivation
 * ou à la suppression d'un utilisateur, ses sièges sont libérés et ses demandes en attente refusées
 * (dans la même transaction que la désactivation).
 */
@Service
@RequiredArgsConstructor
public class LicenseService implements UserDeletionGuard {

    public static final String REPORTS = "license_reports";
    static final int RENEWAL_ALERT_DAYS = 30;
    static final int UNUSED_DAYS = 90;
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private final SoftwareRepository software;
    private final SeatRepository seats;
    private final LicenseRequestRepository requests;
    private final ManagerRelations relations;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- §4.4 : libération automatique ----------

    @Override
    public void beforeRemoval(User user) {
        List<Seat> held = seats.findByUserId(user.getId());
        seats.deleteAll(held);
        requests.findByUserIdAndStatus(user.getId(), LicenseRequestStatus.PENDING).forEach(r -> {
            r.setStatus(LicenseRequestStatus.REJECTED);
            r.setRejectReason("Compte désactivé");
            requests.save(r);
        });
        seats.flush();
    }

    // ---------- catalogue ----------

    @Transactional(readOnly = true)
    public List<SoftwareDto> catalog(AuthUser me) {
        return software.findAllByOrderByNameAsc().stream().filter(s -> me.isAdmin() || s.getStatus() == SoftwareStatus.ACTIVE)
                .map(s -> toDto(me, s)).toList();
    }

    @Transactional
    public SoftwareDto create(AuthUser me, SoftwareRequest req) {
        me.requireAdmin();
        if (software.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("SOFTWARE_EXISTS", "Ce logiciel existe déjà");
        }
        Software s = new Software();
        apply(s, req);
        return toDto(me, software.saveAndFlush(s));
    }

    @Transactional
    public SoftwareDto update(AuthUser me, UUID id, SoftwareRequest req) {
        me.requireAdmin();
        Software s = software.lock(id).orElseThrow(() -> ApiException.notFound("Logiciel"));
        long assigned = seats.countBySoftwareId(id);
        if (req.seats() < assigned) {
            throw ApiException.conflict("SEATS_BELOW_ASSIGNED", assigned + " sièges sont attribués : retirez-en avant de réduire");
        }
        if (!s.getName().equalsIgnoreCase(req.name().trim()) && software.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("SOFTWARE_EXISTS", "Ce logiciel existe déjà");
        }
        apply(s, req);
        return toDto(me, software.saveAndFlush(s));
    }

    private void apply(Software s, SoftwareRequest req) {
        s.setName(req.name().trim());
        s.setVendor(req.vendor().trim());
        s.setSeats(req.seats());
        s.setCostPerSeat(req.costPerSeat());
        s.setBilling(req.billing());
        s.setRenewalDate(req.renewalDate());
        s.setStatus(req.status() == null ? SoftwareStatus.ACTIVE : req.status());
    }

    // ---------- sièges ----------

    @Transactional(readOnly = true)
    public List<SeatDto> holders(AuthUser me, UUID id) {
        me.requireAdmin();
        find(id);
        return seats.findBySoftwareIdOrderByCreatedAtAsc(id).stream().map(this::toDto).toList();
    }

    @Transactional
    public SeatDto assign(AuthUser me, UUID id, UUID userId) {
        me.requireAdmin();
        User u = userLookup.require(userId);
        if (!u.isEnabled()) {
            throw ValidationException.field("userId", "compte désactivé");
        }
        return toDto(assignLocked(id, userId));
    }

    /** Attribution sous verrou du logiciel (aussi utilisée par l'approbation d'une demande). */
    private Seat assignLocked(UUID softwareId, UUID userId) {
        Software s = software.lock(softwareId).orElseThrow(() -> ApiException.notFound("Logiciel"));
        if (s.getStatus() == SoftwareStatus.TERMINATED) {
            throw ApiException.conflict("SOFTWARE_TERMINATED", "Abonnement résilié");
        }
        if (seats.findBySoftwareIdAndUserId(softwareId, userId).isPresent()) {
            throw ApiException.conflict("ALREADY_ASSIGNED", "Cette personne a déjà un siège");
        }
        if (seats.countBySoftwareId(softwareId) >= s.getSeats()) {
            throw ApiException.conflict("NO_SEAT_LEFT", "Plus aucun siège disponible sur " + s.getName());
        }
        Seat seat = new Seat();
        seat.setSoftwareId(softwareId);
        seat.setUserId(userId);
        return seats.saveAndFlush(seat);
    }

    @Transactional
    public void unassign(AuthUser me, UUID id, UUID userId) {
        me.requireAdmin();
        software.lock(id).orElseThrow(() -> ApiException.notFound("Logiciel"));
        Seat seat = seats.findBySoftwareIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Siège"));
        seats.delete(seat);
    }

    @Transactional
    public MyLicenseDto declareUsage(AuthUser me, UUID softwareId) {
        Seat seat = seats.findBySoftwareIdAndUserId(softwareId, me.id()).orElseThrow(() -> ApiException.notFound("Licence"));
        seat.setLastUsedOn(today());
        seats.saveAndFlush(seat);
        Software s = find(softwareId);
        return new MyLicenseDto(s.getId(), s.getName(), s.getVendor(), seat.getCreatedAt(), seat.getLastUsedOn());
    }

    // ---------- demandes ----------

    @Transactional
    public LicenseRequestDto request(AuthUser me, AskLicenseRequest req) {
        Software s = find(req.softwareId());
        if (s.getStatus() == SoftwareStatus.TERMINATED) {
            throw ApiException.conflict("SOFTWARE_TERMINATED", "Abonnement résilié : plus de demande possible");
        }
        if (seats.findBySoftwareIdAndUserId(s.getId(), me.id()).isPresent()) {
            throw ApiException.conflict("ALREADY_ASSIGNED", "Vous avez déjà une licence");
        }
        if (requests.existsBySoftwareIdAndUserIdAndStatus(s.getId(), me.id(), LicenseRequestStatus.PENDING)) {
            throw ApiException.conflict("REQUEST_PENDING", "Une demande est déjà en attente");
        }
        LicenseRequest r = new LicenseRequest();
        r.setSoftwareId(s.getId());
        r.setUserId(me.id());
        r.setReason(req.reason().trim());
        r.setStatus(LicenseRequestStatus.PENDING);
        return toDto(requests.saveAndFlush(r));
    }

    @Transactional(readOnly = true)
    public List<LicenseRequestDto> requests(AuthUser me, LicenseRequestStatus status) {
        return (me.isAdmin() ? requests.findAllByOrderByCreatedAtDesc() : requests.findByUserIdOrderByCreatedAtDesc(me.id())).stream()
                .filter(r -> status == null || r.getStatus() == status).map(this::toDto).toList();
    }

    @Transactional
    public LicenseRequestDto approve(AuthUser me, UUID id) {
        me.requireAdmin();
        LicenseRequest r = pending(id);
        assignLocked(r.getSoftwareId(), r.getUserId());
        r.setStatus(LicenseRequestStatus.APPROVED);
        r.setDecidedBy(me.id());
        return toDto(requests.saveAndFlush(r));
    }

    @Transactional
    public LicenseRequestDto reject(AuthUser me, UUID id, String reason) {
        me.requireAdmin();
        LicenseRequest r = pending(id);
        r.setStatus(LicenseRequestStatus.REJECTED);
        r.setRejectReason(reason.trim());
        r.setDecidedBy(me.id());
        return toDto(requests.saveAndFlush(r));
    }

    @Transactional(readOnly = true)
    public List<MyLicenseDto> mine(AuthUser me) {
        Map<UUID, Software> byId = software.findAll().stream().collect(Collectors.toMap(Software::getId, Function.identity()));
        return seats.findByUserId(me.id()).stream().map(seat -> {
            Software s = byId.get(seat.getSoftwareId());
            return new MyLicenseDto(s.getId(), s.getName(), s.getVendor(), seat.getCreatedAt(), seat.getLastUsedOn());
        }).sorted(Comparator.comparing(MyLicenseDto::name)).toList();
    }

    // ---------- alertes et coûts ----------

    @Transactional(readOnly = true)
    public AlertsDto alerts(AuthUser me) {
        me.requireAdmin();
        LocalDate limit = today().plusDays(RENEWAL_ALERT_DAYS);
        List<Software> active = software.findAllByOrderByNameAsc().stream().filter(s -> s.getStatus() == SoftwareStatus.ACTIVE).toList();
        List<SoftwareDto> renewals = active.stream().filter(s -> !s.getRenewalDate().isAfter(limit))
                .sorted(Comparator.comparing(Software::getRenewalDate)).map(s -> toDto(me, s)).toList();
        List<UnusedSeat> unused = new ArrayList<>();
        for (Software s : active) {
            for (Seat seat : seats.findBySoftwareIdOrderByCreatedAtAsc(s.getId())) {
                if (isUnused(seat)) {
                    unused.add(new UnusedSeat(s.getId(), s.getName(), seat.getUserId(), userLookup.name(seat.getUserId()), seat.getLastUsedOn(), monthly(s)));
                }
            }
        }
        return new AlertsDto(renewals, unused);
    }

    /** Coût mensuel = sièges attribués × coût ramené au mois ; équipe = manager du détenteur. */
    @Transactional(readOnly = true)
    public CostsDto costs(AuthUser me, YearMonth month) {
        me.requireAdmin();
        YearMonth m = month == null ? YearMonth.now(clock.withZone(ZONE)) : month;
        List<Software> active = software.findAllByOrderByNameAsc().stream().filter(s -> s.getStatus() == SoftwareStatus.ACTIVE).toList();
        List<CostRow> bySoftware = new ArrayList<>();
        Map<String, long[]> teamSeats = new TreeMap<>();
        Map<String, BigDecimal> teamCost = new TreeMap<>();
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (Software s : active) {
            List<Seat> held = seats.findBySoftwareIdOrderByCreatedAtAsc(s.getId());
            BigDecimal cost = monthly(s).multiply(BigDecimal.valueOf(held.size())).setScale(2, RoundingMode.HALF_UP);
            bySoftware.add(new CostRow(s.getName(), held.size(), cost));
            total = total.add(cost);
            for (Seat seat : held) {
                String team = relations.managerOf(REPORTS, seat.getUserId()).map(mg -> "Équipe de " + userLookup.name(mg)).orElse("Sans équipe");
                teamSeats.computeIfAbsent(team, k -> new long[1])[0]++;
                teamCost.merge(team, monthly(s), BigDecimal::add);
            }
        }
        List<CostRow> byTeam = teamCost.entrySet().stream()
                .map(e -> new CostRow(e.getKey(), teamSeats.get(e.getKey())[0], e.getValue().setScale(2, RoundingMode.HALF_UP)))
                .sorted(Comparator.comparing(CostRow::monthlyCost).reversed()).toList();
        bySoftware.sort(Comparator.comparing(CostRow::monthlyCost).reversed());
        return new CostsDto(m.toString(), total, bySoftware, byTeam);
    }

    // ---------- outils ----------

    private BigDecimal monthly(Software s) {
        return s.getBilling() == Billing.YEARLY ? s.getCostPerSeat().divide(TWELVE, 2, RoundingMode.HALF_UP) : s.getCostPerSeat();
    }

    private boolean isUnused(Seat seat) {
        LocalDate reference = seat.getLastUsedOn() != null ? seat.getLastUsedOn() : seat.getCreatedAt().atZone(ZONE).toLocalDate();
        return reference.isBefore(today().minusDays(UNUSED_DAYS));
    }

    private LicenseRequest pending(UUID id) {
        LicenseRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("Demande"));
        if (r.getStatus() != LicenseRequestStatus.PENDING) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Demande déjà " + r.getStatus());
        }
        return r;
    }

    private Software find(UUID id) {
        return software.findById(id).orElseThrow(() -> ApiException.notFound("Logiciel"));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private SoftwareDto toDto(AuthUser me, Software s) {
        long assigned = seats.countBySoftwareId(s.getId());
        return new SoftwareDto(s.getId(), s.getName(), s.getVendor(), s.getSeats(), assigned, (int) Math.max(0, s.getSeats() - assigned),
                s.getCostPerSeat(), s.getBilling(), monthly(s), s.getRenewalDate(), s.getStatus(),
                seats.findBySoftwareIdAndUserId(s.getId(), me.id()).isPresent(),
                requests.existsBySoftwareIdAndUserIdAndStatus(s.getId(), me.id(), LicenseRequestStatus.PENDING));
    }

    private SeatDto toDto(Seat seat) {
        return new SeatDto(seat.getUserId(), userLookup.name(seat.getUserId()), seat.getCreatedAt(), seat.getLastUsedOn(), isUnused(seat));
    }

    private LicenseRequestDto toDto(LicenseRequest r) {
        return new LicenseRequestDto(r.getId(), r.getSoftwareId(), find(r.getSoftwareId()).getName(), r.getUserId(), userLookup.name(r.getUserId()),
                r.getReason(), r.getStatus(), r.getRejectReason(), r.getCreatedAt());
    }
}
