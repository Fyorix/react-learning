package fr.epita.platform.apps.budget.service;

import fr.epita.platform.apps.budget.dto.BudgetDtos.*;
import fr.epita.platform.apps.budget.entity.*;
import fr.epita.platform.apps.budget.repository.*;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Restant d'une catégorie = enveloppe − engagé (demandes APPROVED, montant estimé) − dépensé (SPENT, montant réel).
 * Les validations d'une équipe sont sérialisées par un verrou sur l'équipe.
 */
@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final BudgetTeamRepository teams;
    private final FiscalYearRepository years;
    private final EnvelopeRepository envelopes;
    private final PurchaseRequestRepository requests;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- équipes ----------

    @Transactional(readOnly = true)
    public List<TeamDto> teams(AuthUser me) {
        return teams.findAllByOrderByNameAsc().stream()
                .filter(t -> me.isAdmin() || t.getManagerId().equals(me.id()) || t.getMemberIds().contains(me.id()))
                .map(t -> toDto(me, t)).toList();
    }

    @Transactional
    public TeamDto createTeam(AuthUser me, TeamRequest req) {
        me.requireAdmin();
        if (teams.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("TEAM_EXISTS", "Une équipe porte déjà ce nom");
        }
        BudgetTeam t = new BudgetTeam();
        apply(t, req);
        return toDto(me, teams.saveAndFlush(t));
    }

    @Transactional
    public TeamDto updateTeam(AuthUser me, UUID id, TeamRequest req) {
        me.requireAdmin();
        BudgetTeam t = team(id);
        apply(t, req);
        return toDto(me, teams.saveAndFlush(t));
    }

    private void apply(BudgetTeam t, TeamRequest req) {
        userLookup.require(req.managerId());
        req.memberIds().forEach(userLookup::require);
        t.setName(req.name().trim());
        t.setManagerId(req.managerId());
        t.setMemberIds(new HashSet<>(req.memberIds()));
    }

    // ---------- enveloppes et exercices ----------

    @Transactional
    public DashboardDto setEnvelopes(AuthUser me, UUID teamId, EnvelopesRequest req) {
        me.requireAdmin();
        team(teamId);
        FiscalYear y = openYear(req.year());
        for (EnvelopeItem item : req.envelopes()) {
            Envelope.Key key = new Envelope.Key(teamId, y.getYear(), item.category());
            Envelope e = envelopes.findById(key).orElseGet(() -> new Envelope(key, ZERO));
            e.setAmount(item.amount());
            envelopes.save(e);
        }
        envelopes.flush();
        return dashboard(me, teamId, req.year());
    }

    @Transactional
    public void closeYear(AuthUser me, int year) {
        me.requireAdmin();
        FiscalYear y = years.findById(year).orElseThrow(() -> ApiException.notFound("Exercice"));
        if (y.getStatus() == YearStatus.CLOSED) {
            throw ApiException.conflict("FISCAL_YEAR_CLOSED", "Exercice déjà clos");
        }
        y.setStatus(YearStatus.CLOSED);
        years.saveAndFlush(y);
    }

    /** Exercice ouvert, créé à la première utilisation ; 409 s'il est clos. */
    private FiscalYear openYear(int year) {
        FiscalYear y = years.findById(year).orElseGet(() -> years.saveAndFlush(new FiscalYear(year, YearStatus.OPEN)));
        if (y.getStatus() == YearStatus.CLOSED) {
            throw ApiException.conflict("FISCAL_YEAR_CLOSED", "L'exercice " + year + " est clos");
        }
        return y;
    }

    // ---------- tableau de bord ----------

    @Transactional(readOnly = true)
    public DashboardDto dashboard(AuthUser me, UUID teamId, Integer year) {
        BudgetTeam t = visibleTeam(me, teamId);
        int y = year == null ? today().getYear() : year;
        Map<BudgetCategory, BigDecimal> planned = envelopes.findByIdTeamIdAndIdYear(teamId, y).stream()
                .collect(Collectors.toMap(e -> e.getId().getCategory(), Envelope::getAmount));
        List<PurchaseRequest> rs = requests.findByTeamIdAndYear(teamId, y);
        List<CategoryDto> cats = new ArrayList<>();
        for (BudgetCategory c : BudgetCategory.values()) {
            BigDecimal p = planned.getOrDefault(c, ZERO);
            BigDecimal committed = sum(rs, c, PurchaseStatus.APPROVED);
            BigDecimal spent = sum(rs, c, PurchaseStatus.SPENT);
            BigDecimal used = committed.add(spent);
            int consumption = p.signum() == 0 ? (used.signum() > 0 ? 100 : 0) : used.multiply(BigDecimal.valueOf(100)).divide(p, 0, RoundingMode.DOWN).intValue();
            cats.add(new CategoryDto(c, p, committed, spent, p.subtract(used), consumption, consumption >= 100 ? Integer.valueOf(100) : consumption >= 80 ? Integer.valueOf(80) : null));
        }
        YearStatus status = years.findById(y).map(FiscalYear::getStatus).orElse(YearStatus.OPEN);
        return new DashboardDto(teamId, t.getName(), y, status, cats,
                cats.stream().map(CategoryDto::planned).reduce(ZERO, BigDecimal::add),
                cats.stream().map(CategoryDto::committed).reduce(ZERO, BigDecimal::add),
                cats.stream().map(CategoryDto::spent).reduce(ZERO, BigDecimal::add),
                cats.stream().map(CategoryDto::remaining).reduce(ZERO, BigDecimal::add));
    }

    private BigDecimal remaining(UUID teamId, int year, BudgetCategory c, UUID excludeRequest) {
        BigDecimal planned = envelopes.findById(new Envelope.Key(teamId, year, c)).map(Envelope::getAmount).orElse(ZERO);
        List<PurchaseRequest> rs = requests.findByTeamIdAndYear(teamId, year).stream().filter(r -> !r.getId().equals(excludeRequest)).toList();
        return planned.subtract(sum(rs, c, PurchaseStatus.APPROVED)).subtract(sum(rs, c, PurchaseStatus.SPENT));
    }

    private static BigDecimal sum(List<PurchaseRequest> rs, BudgetCategory c, PurchaseStatus s) {
        return rs.stream().filter(r -> r.getCategory() == c && r.getStatus() == s)
                .map(r -> s == PurchaseStatus.SPENT ? r.getSpentAmount() : r.getEstimatedAmount()).reduce(ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public List<AlertDto> alerts(AuthUser me) {
        int y = today().getYear();
        List<AlertDto> out = new ArrayList<>();
        for (BudgetTeam t : teams.findAllByOrderByNameAsc()) {
            if (!me.isAdmin() && !t.getManagerId().equals(me.id())) {
                continue;
            }
            for (CategoryDto c : dashboard(me, t.getId(), y).categories()) {
                if (c.alert() != null) {
                    out.add(new AlertDto(t.getId(), t.getName(), y, c.category(), c.consumption(), c.alert()));
                }
            }
        }
        return out;
    }

    // ---------- demandes ----------

    @Transactional
    public PurchaseDto create(AuthUser me, PurchaseRequestBody req) {
        BudgetTeam t = team(req.teamId());
        if (!t.getMemberIds().contains(me.id()) && !t.getManagerId().equals(me.id())) {
            throw ApiException.forbidden("Réservé aux membres de l'équipe");
        }
        FiscalYear y = openYear(today().getYear());
        PurchaseRequest r = new PurchaseRequest();
        r.setTeamId(t.getId());
        r.setYear(y.getYear());
        r.setRequesterId(me.id());
        r.setCategory(req.category());
        r.setLabel(req.label().trim());
        r.setEstimatedAmount(req.estimatedAmount());
        r.setSupplier(req.supplier() == null || req.supplier().isBlank() ? null : req.supplier().trim());
        r.setStatus(PurchaseStatus.PENDING);
        return toDto(requests.saveAndFlush(r));
    }

    @Transactional(readOnly = true)
    public List<PurchaseDto> list(AuthUser me, UUID teamId, PurchaseStatus status) {
        List<BudgetTeam> visible = teamId == null ? teams.findAllByOrderByNameAsc().stream()
                .filter(t -> me.isAdmin() || t.getManagerId().equals(me.id()) || t.getMemberIds().contains(me.id())).toList()
                : List.of(visibleTeam(me, teamId));
        return visible.stream().flatMap(t -> requests.findByTeamIdOrderByCreatedAtDesc(t.getId()).stream())
                .filter(r -> status == null || r.getStatus() == status)
                .sorted(Comparator.comparing(PurchaseRequest::getCreatedAt).reversed()).map(this::toDto).toList();
    }

    /** Validation : le montant est engagé ; au-delà du restant, dérogation ADMIN motivée uniquement. */
    @Transactional
    public PurchaseDto approve(AuthUser me, UUID id, ApproveRequest req) {
        PurchaseRequest r = pending(id);
        BudgetTeam t = teams.lock(r.getTeamId()).orElseThrow();
        requireManager(me, t);
        openYear(r.getYear());
        BigDecimal left = remaining(t.getId(), r.getYear(), r.getCategory(), r.getId());
        boolean override = req != null && req.override();
        if (r.getEstimatedAmount().compareTo(left) > 0) {
            if (!override) {
                throw ApiException.conflict("BUDGET_EXCEEDED", "Montant supérieur au restant de la catégorie (" + left + " €)");
            }
            if (!me.isAdmin()) {
                throw ApiException.forbidden("Seul un administrateur peut déroger au budget");
            }
            if (req.reason() == null || req.reason().isBlank()) {
                throw ValidationException.field("reason", "motif obligatoire pour une dérogation");
            }
            r.setOverrideReason(req.reason().trim());
        }
        r.setStatus(PurchaseStatus.APPROVED);
        r.setDecidedBy(me.id());
        return toDto(requests.saveAndFlush(r));
    }

    @Transactional
    public PurchaseDto reject(AuthUser me, UUID id, String reason) {
        PurchaseRequest r = pending(id);
        requireManager(me, team(r.getTeamId()));
        r.setStatus(PurchaseStatus.REJECTED);
        r.setReason(reason.trim());
        r.setDecidedBy(me.id());
        return toDto(requests.saveAndFlush(r));
    }

    /** Dépense réelle : remplace l'engagement. */
    @Transactional
    public PurchaseDto spend(AuthUser me, UUID id, SpendRequest req) {
        PurchaseRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("Demande"));
        BudgetTeam t = teams.lock(r.getTeamId()).orElseThrow();
        requireManager(me, t);
        if (r.getStatus() != PurchaseStatus.APPROVED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Seule une demande validée peut être dépensée");
        }
        openYear(r.getYear());
        if (req.date().isAfter(today())) {
            throw ValidationException.field("date", "ne doit pas être dans le futur");
        }
        r.setStatus(PurchaseStatus.SPENT);
        r.setSpentAmount(req.amount());
        r.setSpentOn(req.date());
        return toDto(requests.saveAndFlush(r));
    }

    @Transactional
    public void cancel(AuthUser me, UUID id) {
        PurchaseRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("Demande"));
        if (!r.getRequesterId().equals(me.id())) {
            throw ApiException.forbidden("Réservé à l'auteur de la demande");
        }
        if (r.getStatus() != PurchaseStatus.PENDING) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Seule une demande en attente peut être annulée");
        }
        r.setStatus(PurchaseStatus.CANCELLED);
        requests.saveAndFlush(r);
    }

    // ---------- outils ----------

    private PurchaseRequest pending(UUID id) {
        PurchaseRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("Demande"));
        if (r.getStatus() != PurchaseStatus.PENDING) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Demande déjà " + r.getStatus());
        }
        return r;
    }

    private void requireManager(AuthUser me, BudgetTeam t) {
        if (!me.isAdmin() && !t.getManagerId().equals(me.id())) {
            throw ApiException.forbidden("Réservé au manager de l'équipe");
        }
    }

    private BudgetTeam team(UUID id) {
        return teams.findById(id).orElseThrow(() -> ApiException.notFound("Équipe"));
    }

    private BudgetTeam visibleTeam(AuthUser me, UUID id) {
        BudgetTeam t = team(id);
        if (!me.isAdmin() && !t.getManagerId().equals(me.id()) && !t.getMemberIds().contains(me.id())) {
            throw ApiException.notFound("Équipe");
        }
        return t;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    private TeamDto toDto(AuthUser me, BudgetTeam t) {
        return new TeamDto(t.getId(), t.getName(), t.getManagerId(), userLookup.name(t.getManagerId()), t.getMemberIds().stream().sorted().toList(),
                t.getManagerId().equals(me.id()), t.getMemberIds().contains(me.id()));
    }

    private PurchaseDto toDto(PurchaseRequest r) {
        return new PurchaseDto(r.getId(), r.getTeamId(), team(r.getTeamId()).getName(), r.getYear(), r.getRequesterId(), userLookup.name(r.getRequesterId()),
                r.getCategory(), r.getLabel(), r.getEstimatedAmount(), r.getSupplier(), r.getStatus(), r.getSpentAmount(), r.getSpentOn(),
                r.getDecidedBy() == null ? null : userLookup.name(r.getDecidedBy()), r.getReason(), r.getOverrideReason(), r.getCreatedAt());
    }
}
