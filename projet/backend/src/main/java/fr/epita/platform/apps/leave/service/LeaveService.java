package fr.epita.platform.apps.leave.service;

import fr.epita.platform.apps.leave.dto.LeaveDtos.AbsenceDto;
import fr.epita.platform.apps.leave.dto.LeaveDtos.BalanceDto;
import fr.epita.platform.apps.leave.dto.LeaveDtos.BalanceRequest;
import fr.epita.platform.apps.leave.dto.LeaveDtos.BalancesDto;
import fr.epita.platform.apps.leave.dto.LeaveDtos.LeaveDto;
import fr.epita.platform.apps.leave.dto.LeaveDtos.LeaveRequestBody;
import fr.epita.platform.apps.leave.dto.LeaveDtos.PreviewDto;
import fr.epita.platform.apps.leave.dto.LeaveDtos.ProfileDto;
import fr.epita.platform.apps.leave.entity.LeaveBalance;
import fr.epita.platform.apps.leave.entity.LeaveEnums.Half;
import fr.epita.platform.apps.leave.entity.LeaveEnums.LeaveStatus;
import fr.epita.platform.apps.leave.entity.LeaveEnums.LeaveType;
import fr.epita.platform.apps.leave.entity.LeaveRequest;
import fr.epita.platform.apps.leave.repository.LeaveBalanceRepository;
import fr.epita.platform.apps.leave.repository.LeaveRequestRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.CalendarRules;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import fr.epita.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveService {

    public static final String REPORTS = "leave_reports";
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final ManagerRelations relations;
    private final UserRepository users;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- calcul ----------

    /** Nombre de jours ouvrés décomptés, demi-journées comprises. */
    public static BigDecimal countDays(LocalDate start, LocalDate end, Half startHalf, Half endHalf) {
        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!CalendarRules.isWorkingDay(d)) {
                continue;
            }
            BigDecimal day = BigDecimal.ONE;
            if (d.equals(start) && startHalf == Half.AFTERNOON) {
                day = day.subtract(HALF);
            }
            if (d.equals(end) && endHalf == Half.MORNING) {
                day = day.subtract(HALF);
            }
            total = total.add(day.max(BigDecimal.ZERO));
        }
        return total;
    }

    private BigDecimal validateAndCount(LeaveRequestBody req) {
        Half sh = req.startHalf() == null ? Half.MORNING : req.startHalf();
        Half eh = req.endHalf() == null ? Half.AFTERNOON : req.endHalf();
        var errors = new ValidationException.Collector();
        errors.check(!req.endDate().isBefore(req.startDate()), "endDate", "doit être postérieure ou égale à startDate");
        errors.check(req.startDate().getYear() == req.endDate().getYear(), "endDate", "doit être dans la même année que startDate");
        errors.check(!(req.startDate().equals(req.endDate()) && sh == Half.AFTERNOON && eh == Half.MORNING),
                "endHalf", "une demande d'un jour ne peut pas commencer l'après-midi et finir le matin");
        errors.throwIfAny();
        BigDecimal days = countDays(req.startDate(), req.endDate(), sh, eh);
        if (days.signum() == 0) {
            throw ValidationException.field("startDate", "la période ne contient aucun jour ouvré");
        }
        return days;
    }

    // ---------- demandes ----------

    @Transactional
    public LeaveDto request(AuthUser me, LeaveRequestBody req) {
        BigDecimal days = validateAndCount(req);
        LeaveRequest r = new LeaveRequest();
        r.setUserId(me.id());
        r.setType(req.type());
        r.setStartDate(req.startDate());
        r.setEndDate(req.endDate());
        r.setStartHalf(req.startHalf() == null ? Half.MORNING : req.startHalf());
        r.setEndHalf(req.endHalf() == null ? Half.AFTERNOON : req.endHalf());
        r.setDays(days);
        r.setComment(req.comment() == null || req.comment().isBlank() ? null : req.comment().trim());
        // verrou applicatif simple : on relit les demandes du salarié dans la transaction
        List<LeaveRequest> mine = requests.findByUserIdOrderByStartDateDesc(me.id());
        boolean overlap = mine.stream().filter(LeaveRequest::isActive)
                .anyMatch(o -> o.firstSlot() <= r.lastSlot() && r.firstSlot() <= o.lastSlot());
        if (overlap) {
            throw ApiException.conflict("LEAVE_OVERLAP", "Cette période chevauche une autre demande");
        }
        if (req.type() != LeaveType.UNPAID) {
            BigDecimal remaining = balanceOf(me.id(), req.startDate().getYear(), req.type()).remaining();
            if (days.compareTo(remaining) > 0) {
                throw ApiException.conflict("INSUFFICIENT_BALANCE",
                        "Solde insuffisant : " + remaining + " jour(s) disponible(s) pour " + days + " demandé(s)");
            }
        }
        return toDto(requests.saveAndFlush(r), me);
    }

    @Transactional(readOnly = true)
    public List<LeaveDto> mine(AuthUser me, Integer year) {
        return requests.findByUserIdOrderByStartDateDesc(me.id()).stream()
                .filter(r -> year == null || r.getStartDate().getYear() == year)
                .map(r -> toDto(r, me)).toList();
    }

    /** Annulation : une demande en attente à tout moment, une demande validée seulement si elle n'a pas commencé. */
    @Transactional
    public LeaveDto cancel(AuthUser me, UUID id) {
        LeaveRequest r = find(id);
        if (!r.getUserId().equals(me.id())) {
            throw ApiException.forbidden("Seul le salarié peut annuler sa demande");
        }
        if (!r.isActive()) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Cette demande est déjà " + r.getStatus());
        }
        if (r.getStatus() == LeaveStatus.APPROVED && !r.getStartDate().isAfter(LocalDate.now(clock))) {
            throw ApiException.conflict("LEAVE_STARTED", "Ce congé a déjà commencé");
        }
        r.setStatus(LeaveStatus.CANCELLED);
        return toDto(r, me);
    }

    @Transactional(readOnly = true)
    public List<LeaveDto> teamRequests(AuthUser me, LeaveStatus status) {
        List<UUID> team = me.isAdmin() ? users.findAll().stream().map(u -> u.getId()).toList()
                : relations.employeesOf(REPORTS, me.id());
        if (team.isEmpty()) {
            return List.of();
        }
        return requests.findByUserIdInOrderByStartDateAsc(team).stream()
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> !r.getUserId().equals(me.id()))
                .map(r -> toDto(r, me)).toList();
    }

    @Transactional
    public LeaveDto approve(AuthUser me, UUID id) {
        LeaveRequest r = decidable(me, id);
        r.setStatus(LeaveStatus.APPROVED);
        r.setDecidedBy(me.id());
        r.setDecidedAt(clock.instant());
        return toDto(r, me);
    }

    @Transactional
    public LeaveDto reject(AuthUser me, UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ValidationException.field("reason", "ne doit pas être vide");
        }
        LeaveRequest r = decidable(me, id);
        r.setStatus(LeaveStatus.REJECTED);
        r.setReason(reason.trim());
        r.setDecidedBy(me.id());
        r.setDecidedAt(clock.instant());
        return toDto(r, me);
    }

    // ---------- soldes ----------

    @Transactional(readOnly = true)
    public BalancesDto balances(AuthUser me, Integer year) {
        return balancesOf(me.id(), year == null ? LocalDate.now(clock).getYear() : year);
    }

    @Transactional(readOnly = true)
    public List<BalancesDto> allBalances(AuthUser me, Integer year) {
        me.requireAdmin();
        int y = year == null ? LocalDate.now(clock).getYear() : year;
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream().map(u -> balancesOf(u.getId(), y)).toList();
    }

    @Transactional
    public BalancesDto setBalance(AuthUser me, UUID userId, BalanceRequest req) {
        me.requireAdmin();
        userLookup.require(userId);
        LeaveBalance b = balances.findById(new LeaveBalance.Key(userId, req.year())).orElseGet(() -> {
            LeaveBalance nb = new LeaveBalance();
            nb.setId(new LeaveBalance.Key(userId, req.year()));
            return nb;
        });
        b.setPaid(halfDay(req.paid(), "paid"));
        b.setRtt(halfDay(req.rtt(), "rtt"));
        balances.saveAndFlush(b);
        return balancesOf(userId, req.year());
    }

    @Transactional(readOnly = true)
    public PreviewDto preview(AuthUser me, LeaveRequestBody req) {
        BigDecimal days = validateAndCount(req);
        if (req.type() == LeaveType.UNPAID) {
            return new PreviewDto(days, null, null, true);
        }
        BigDecimal remaining = balanceOf(me.id(), req.startDate().getYear(), req.type()).remaining();
        return new PreviewDto(days, remaining, remaining.subtract(days), days.compareTo(remaining) <= 0);
    }

    // ---------- équipe ----------

    @Transactional(readOnly = true)
    public ProfileDto profile(AuthUser me) {
        UUID manager = relations.managerOf(REPORTS, me.id()).orElse(null);
        return new ProfileDto(manager, userLookup.name(manager), relations.employeesOf(REPORTS, me.id()));
    }

    /** Équipe = moi, mon manager, mes collègues (même manager) et mes salariés. Le type n'est visible que du manager. */
    @Transactional(readOnly = true)
    public List<AbsenceDto> teamCalendar(AuthUser me, YearMonth month) {
        YearMonth m = month == null ? YearMonth.now(clock) : month;
        Set<UUID> team = new LinkedHashSet<>();
        team.add(me.id());
        relations.managerOf(REPORTS, me.id()).ifPresent(manager -> {
            team.add(manager);
            team.addAll(relations.employeesOf(REPORTS, manager));
        });
        team.addAll(relations.employeesOf(REPORTS, me.id()));
        List<LeaveRequest> list = requests.approvedBetween(team, m.atDay(1), m.atEndOfMonth());
        Map<UUID, String> names = userLookup.namesOf(list, LeaveRequest::getUserId);
        List<AbsenceDto> out = new ArrayList<>();
        for (LeaveRequest r : list) {
            boolean seeType = me.isAdmin() || r.getUserId().equals(me.id())
                    || relations.isManagerOf(REPORTS, me.id(), r.getUserId());
            out.add(new AbsenceDto(r.getId(), r.getUserId(), names.get(r.getUserId()), seeType ? r.getType() : null,
                    r.getStartDate(), r.getEndDate(), r.getStartHalf(), r.getEndHalf()));
        }
        return out;
    }

    // ---------- outils ----------

    private BalancesDto balancesOf(UUID userId, int year) {
        List<BalanceDto> list = List.of(balanceOf(userId, year, LeaveType.PAID), balanceOf(userId, year, LeaveType.RTT));
        return new BalancesDto(userId, userLookup.name(userId), year, list);
    }

    BalanceDto balanceOf(UUID userId, int year, LeaveType type) {
        BigDecimal acquired = balances.findById(new LeaveBalance.Key(userId, year))
                .map(b -> type == LeaveType.PAID ? b.getPaid() : b.getRtt()).orElse(BigDecimal.ZERO);
        BigDecimal taken = BigDecimal.ZERO;
        BigDecimal pending = BigDecimal.ZERO;
        for (LeaveRequest r : requests.findByUserIdOrderByStartDateDesc(userId)) {
            if (r.getType() != type || r.getStartDate().getYear() != year) {
                continue;
            }
            if (r.getStatus() == LeaveStatus.APPROVED) {
                taken = taken.add(r.getDays());
            } else if (r.getStatus() == LeaveStatus.PENDING) {
                pending = pending.add(r.getDays());
            }
        }
        return new BalanceDto(type, acquired, taken, pending, acquired.subtract(taken).subtract(pending));
    }

    private LeaveRequest decidable(AuthUser me, UUID id) {
        LeaveRequest r = find(id);
        if (r.getUserId().equals(me.id())) {
            throw ApiException.forbidden("Un manager ne valide pas ses propres congés");
        }
        if (!me.isAdmin() && !relations.isManagerOf(REPORTS, me.id(), r.getUserId())) {
            throw ApiException.forbidden("Seul le manager du salarié peut décider");
        }
        if (r.getStatus() != LeaveStatus.PENDING) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Seule une demande en attente peut être validée ou refusée");
        }
        return r;
    }

    private static BigDecimal halfDay(BigDecimal value, String field) {
        if (value.multiply(BigDecimal.TWO).stripTrailingZeros().scale() > 0) {
            throw ValidationException.field(field, "doit être un multiple de 0,5");
        }
        return value;
    }

    private LeaveRequest find(UUID id) {
        return requests.findById(id).orElseThrow(() -> ApiException.notFound("Demande"));
    }

    private LeaveDto toDto(LeaveRequest r, AuthUser me) {
        boolean canCancel = r.getUserId().equals(me.id()) && (r.getStatus() == LeaveStatus.PENDING
                || (r.getStatus() == LeaveStatus.APPROVED && r.getStartDate().isAfter(LocalDate.now(clock))));
        return new LeaveDto(r.getId(), r.getUserId(), userLookup.name(r.getUserId()), r.getType(), r.getStartDate(),
                r.getEndDate(), r.getStartHalf(), r.getEndHalf(), r.getDays(), r.getStatus(), r.getComment(),
                r.getReason(), userLookup.name(r.getDecidedBy()), r.getDecidedAt(), canCancel, r.getCreatedAt());
    }
}
