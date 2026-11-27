package fr.epita.platform.apps.goals.service;

import fr.epita.platform.apps.goals.dto.GoalDtos.EmployeeStatsDto;
import fr.epita.platform.apps.goals.dto.GoalDtos.GoalDto;
import fr.epita.platform.apps.goals.dto.GoalDtos.GoalRequest;
import fr.epita.platform.apps.goals.dto.GoalDtos.ProgressDto;
import fr.epita.platform.apps.goals.dto.GoalDtos.ReportDto;
import fr.epita.platform.apps.goals.dto.GoalDtos.ReviewRequest;
import fr.epita.platform.apps.goals.entity.Goal;
import fr.epita.platform.apps.goals.entity.GoalProgress;
import fr.epita.platform.apps.goals.entity.GoalReport;
import fr.epita.platform.apps.goals.entity.GoalStatus;
import fr.epita.platform.apps.goals.repository.GoalProgressRepository;
import fr.epita.platform.apps.goals.repository.GoalReportRepository;
import fr.epita.platform.apps.goals.repository.GoalRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GoalService {

    public static final int MAX_GOALS_IN_PROGRESS = 5;
    public static final int MAX_MONTHS = 12;

    private final GoalRepository goals;
    private final GoalProgressRepository progress;
    private final GoalReportRepository reports;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- lecture ----------

    @Transactional
    public List<GoalDto> mine(AuthUser me, GoalStatus status) {
        closeOverdue();
        return toDtos(goals.findByEmployeeIdOrderByDueDateAsc(me.id()), me, status, null);
    }

    @Transactional
    public List<GoalDto> team(AuthUser me, UUID employeeId, GoalStatus status) {
        closeOverdue();
        Set<UUID> employees = reports.findByManagerId(me.id()).stream().map(GoalReport::getEmployeeId)
                .collect(Collectors.toSet());
        if (employeeId != null) {
            if (!employees.contains(employeeId)) {
                throw ApiException.forbidden("Ce salarié ne fait pas partie de votre équipe");
            }
            employees = Set.of(employeeId);
        }
        return toDtos(goals.findByEmployeeIdInOrderByDueDateAsc(employees), me, status, null);
    }

    @Transactional
    public GoalDto get(AuthUser me, UUID id) {
        closeOverdue();
        Goal goal = find(id);
        if (!goal.getEmployeeId().equals(me.id()) && !canManage(me, goal.getEmployeeId())) {
            throw ApiException.forbidden("Objectif réservé au salarié et à son manager");
        }
        return toDtos(List.of(goal), me, null, progress.findByGoalIdOrderByCreatedAtAsc(id)).getFirst();
    }

    @Transactional
    public List<EmployeeStatsDto> teamStats(AuthUser me) {
        closeOverdue();
        List<UUID> employees = reports.findByManagerId(me.id()).stream().map(GoalReport::getEmployeeId).toList();
        Map<UUID, List<Goal>> byEmployee = goals.findByEmployeeIdInOrderByDueDateAsc(employees).stream()
                .collect(Collectors.groupingBy(Goal::getEmployeeId));
        Map<UUID, String> names = userLookup.names(employees);
        return employees.stream().map(id -> {
            List<Goal> list = byEmployee.getOrDefault(id, List.of());
            long validated = count(list, GoalStatus.VALIDATED);
            long notValidated = count(list, GoalStatus.NOT_VALIDATED);
            int avg = (int) Math.round(list.stream().mapToInt(Goal::progressPercent).average().orElse(0));
            Integer rate = validated + notValidated == 0 ? null
                    : (int) Math.round(validated * 100.0 / (validated + notValidated));
            return new EmployeeStatsDto(id, names.get(id), list.size(), count(list, GoalStatus.IN_PROGRESS),
                    count(list, GoalStatus.SUBMITTED), validated, notValidated, avg, rate);
        }).sorted((a, b) -> a.employeeName().compareToIgnoreCase(b.employeeName())).toList();
    }

    // ---------- manager ----------

    @Transactional
    public GoalDto create(AuthUser me, GoalRequest req) {
        requireManager(me, req.employeeId());
        validateSmart(req);
        if (goals.countByEmployeeIdAndStatus(req.employeeId(), GoalStatus.IN_PROGRESS) >= MAX_GOALS_IN_PROGRESS) {
            throw ApiException.conflict("TOO_MANY_GOALS",
                    "Atteignable : ce salarié a déjà " + MAX_GOALS_IN_PROGRESS + " objectifs en cours");
        }
        Goal goal = new Goal();
        goal.setEmployeeId(req.employeeId());
        goal.setManagerId(me.id());
        apply(goal, req);
        return get(me, goals.saveAndFlush(goal).getId());
    }

    /** Sans avancement : tout est modifiable. Avec avancement (ou soumis) : seule l'échéance peut être repoussée. */
    @Transactional
    public GoalDto update(AuthUser me, UUID id, GoalRequest req) {
        Goal goal = find(id);
        requireManager(me, goal.getEmployeeId());
        if (goal.getStatus() == GoalStatus.VALIDATED || goal.getStatus() == GoalStatus.NOT_VALIDATED) {
            throw ApiException.conflict("GOAL_CLOSED", "L'objectif est clôturé");
        }
        if (!req.employeeId().equals(goal.getEmployeeId())) {
            throw ApiException.badRequest("GOAL_LOCKED", "Individuel : un objectif ne change pas de salarié");
        }
        validateSmart(req);
        boolean locked = progress.existsByGoalId(id) || goal.getStatus() == GoalStatus.SUBMITTED;
        if (locked) {
            boolean onlyDueDate = goal.getTitle().equals(req.title().trim())
                    && goal.getDescription().equals(req.description().trim())
                    && goal.getMetric().equals(req.metric().trim()) && goal.getUnit().equals(req.unit().trim())
                    && goal.getStartValue().compareTo(req.startValue()) == 0
                    && goal.getTargetValue().compareTo(req.targetValue()) == 0;
            if (!onlyDueDate) {
                throw ApiException.conflict("GOAL_LOCKED",
                        "Des avancements existent : seule l'échéance peut être modifiée");
            }
            goal.setDueDate(req.dueDate());
        } else {
            apply(goal, req);
        }
        goals.saveAndFlush(goal);
        return get(me, id);
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        Goal goal = find(id);
        requireManager(me, goal.getEmployeeId());
        if (progress.existsByGoalId(id)) {
            throw ApiException.conflict("GOAL_LOCKED", "Des avancements existent : l'objectif ne peut plus être supprimé");
        }
        goals.delete(goal);
    }

    @Transactional
    public GoalDto review(AuthUser me, UUID id, ReviewRequest req) {
        closeOverdue();
        Goal goal = find(id);
        requireManager(me, goal.getEmployeeId());
        if (goal.getStatus() != GoalStatus.SUBMITTED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Seul un objectif soumis peut être revu");
        }
        String comment = req.comment() == null || req.comment().isBlank() ? null : req.comment().trim();
        switch (req.decision()) {
            case VALIDATED -> goal.setStatus(GoalStatus.VALIDATED);
            case NOT_VALIDATED -> {
                if (comment == null) {
                    throw ValidationException.field("comment", "Un commentaire est obligatoire pour refuser un objectif");
                }
                goal.setStatus(GoalStatus.NOT_VALIDATED);
            }
            case REOPEN -> {
                if (goal.getDueDate().isBefore(LocalDate.now(clock))) {
                    throw ApiException.conflict("GOAL_CLOSED", "L'échéance est passée : repoussez-la avant de rouvrir");
                }
                goal.setStatus(GoalStatus.IN_PROGRESS);
                goal.setSubmittedAt(null);
            }
        }
        goal.setReviewComment(comment);
        goals.saveAndFlush(goal);
        return get(me, id);
    }

    // ---------- salarié ----------

    @Transactional
    public GoalDto recordProgress(AuthUser me, UUID id, BigDecimal value, String comment) {
        closeOverdue();
        Goal goal = find(id);
        if (!goal.getEmployeeId().equals(me.id())) {
            throw ApiException.forbidden("Seul le salarié concerné enregistre son avancement");
        }
        if (goal.getStatus() != GoalStatus.IN_PROGRESS) {
            throw ApiException.conflict("GOAL_CLOSED", "L'objectif n'accepte plus d'avancement");
        }
        GoalProgress p = new GoalProgress();
        p.setGoalId(id);
        p.setValue(value);
        p.setComment(comment == null || comment.isBlank() ? null : comment.trim());
        p.setCreatedAt(clock.instant());
        progress.saveAndFlush(p);
        goal.setCurrentValue(value);
        goals.saveAndFlush(goal);
        return get(me, id);
    }

    @Transactional
    public GoalDto submit(AuthUser me, UUID id) {
        closeOverdue();
        Goal goal = find(id);
        if (!goal.getEmployeeId().equals(me.id())) {
            throw ApiException.forbidden("Seul le salarié concerné peut soumettre son objectif");
        }
        if (goal.getStatus() != GoalStatus.IN_PROGRESS) {
            throw ApiException.conflict("GOAL_CLOSED", "L'objectif a déjà été soumis");
        }
        goal.setStatus(GoalStatus.SUBMITTED);
        goal.setSubmittedAt(clock.instant());
        goals.saveAndFlush(goal);
        return get(me, id);
    }

    // ---------- admin : organigramme ----------

    @Transactional(readOnly = true)
    public List<ReportDto> reports() {
        List<GoalReport> all = reports.findAll();
        Set<UUID> ids = new HashSet<>();
        all.forEach(r -> {
            ids.add(r.getEmployeeId());
            ids.add(r.getManagerId());
        });
        Map<UUID, String> names = userLookup.names(ids);
        return all.stream().map(r -> new ReportDto(r.getEmployeeId(), names.get(r.getEmployeeId()), r.getManagerId(),
                        names.get(r.getManagerId())))
                .sorted((a, b) -> a.employeeName().compareToIgnoreCase(b.employeeName())).toList();
    }

    @Transactional
    public ReportDto assignManager(UUID employeeId, UUID managerId) {
        userLookup.require(employeeId);
        userLookup.require(managerId);
        if (employeeId.equals(managerId)) {
            throw ApiException.conflict("SELF_MANAGER", "Un utilisateur ne peut pas être son propre manager");
        }
        UUID cursor = managerId;
        for (int depth = 0; cursor != null && depth < 100; depth++) {
            if (cursor.equals(employeeId)) {
                throw ApiException.conflict("REPORTING_CYCLE", "Cette affectation créerait une boucle hiérarchique");
            }
            cursor = reports.findById(cursor).map(GoalReport::getManagerId).orElse(null);
        }
        reports.saveAndFlush(new GoalReport(employeeId, managerId));
        return new ReportDto(employeeId, userLookup.name(employeeId), managerId, userLookup.name(managerId));
    }

    @Transactional
    public void removeManager(UUID employeeId) {
        if (!reports.existsById(employeeId)) {
            throw ApiException.notFound("Relation manager");
        }
        reports.deleteById(employeeId);
    }

    /** Chaque nuit, en plus du contrôle à la lecture. */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void closeOverdue() {
        goals.submitOverdue(LocalDate.now(clock), clock.instant());
    }

    // ---------- interne ----------

    private void validateSmart(GoalRequest req) {
        LocalDate today = LocalDate.now(clock);
        new ValidationException.Collector()
                .check(req.targetValue().compareTo(req.startValue()) != 0, "targetValue",
                        "Mesurable : la valeur cible doit différer de la valeur de départ")
                .check(req.dueDate().isAfter(today), "dueDate", "Temporel : l'échéance doit être dans le futur")
                .check(!req.dueDate().isAfter(today.plusMonths(MAX_MONTHS)), "dueDate",
                        "Temporel : l'échéance est à 12 mois maximum")
                .throwIfAny();
    }

    private void apply(Goal goal, GoalRequest req) {
        goal.setTitle(req.title().trim());
        goal.setDescription(req.description().trim());
        goal.setMetric(req.metric().trim());
        goal.setUnit(req.unit().trim());
        goal.setStartValue(req.startValue());
        goal.setTargetValue(req.targetValue());
        goal.setDueDate(req.dueDate());
    }

    private boolean canManage(AuthUser me, UUID employeeId) {
        return me.isAdmin() || reports.findById(employeeId).map(r -> r.getManagerId().equals(me.id())).orElse(false);
    }

    private void requireManager(AuthUser me, UUID employeeId) {
        if (!canManage(me, employeeId)) {
            throw ApiException.forbidden("Réservé au manager du salarié");
        }
    }

    private Goal find(UUID id) {
        return goals.findById(id).orElseThrow(() -> ApiException.notFound("Objectif"));
    }

    private static long count(List<Goal> list, GoalStatus status) {
        return list.stream().filter(g -> g.getStatus() == status).count();
    }

    private List<GoalDto> toDtos(List<Goal> list, AuthUser me, GoalStatus filter, List<GoalProgress> history) {
        List<Goal> filtered = list.stream().filter(g -> filter == null || g.getStatus() == filter).toList();
        Set<UUID> ids = new HashSet<>();
        filtered.forEach(g -> {
            ids.add(g.getEmployeeId());
            ids.add(g.getManagerId());
        });
        Map<UUID, String> names = userLookup.names(ids);
        return filtered.stream().map(g -> {
            boolean hasProgress = history != null ? !history.isEmpty() : progress.existsByGoalId(g.getId());
            boolean manager = canManage(me, g.getEmployeeId());
            boolean employee = g.getEmployeeId().equals(me.id());
            boolean open = g.getStatus() == GoalStatus.IN_PROGRESS || g.getStatus() == GoalStatus.SUBMITTED;
            UUID currentManager = reports.findById(g.getEmployeeId()).map(GoalReport::getManagerId)
                    .orElse(g.getManagerId());
            return new GoalDto(g.getId(), g.getEmployeeId(), names.get(g.getEmployeeId()), currentManager,
                    Optional.ofNullable(names.get(currentManager)).orElseGet(() -> userLookup.name(currentManager)),
                    g.getTitle(), g.getDescription(), g.getMetric(), g.getUnit(), g.getStartValue(),
                    g.getTargetValue(), g.getCurrentValue(), g.progressPercent(), g.getDueDate(), g.getStatus(),
                    g.getReviewComment(), g.getSubmittedAt(), hasProgress, manager && open,
                    employee && g.getStatus() == GoalStatus.IN_PROGRESS,
                    manager && g.getStatus() == GoalStatus.SUBMITTED,
                    history == null ? null : history.stream().filter(Objects::nonNull)
                            .map(p -> new ProgressDto(p.getId(), p.getValue(), p.getComment(), p.getCreatedAt()))
                            .toList());
        }).toList();
    }
}
