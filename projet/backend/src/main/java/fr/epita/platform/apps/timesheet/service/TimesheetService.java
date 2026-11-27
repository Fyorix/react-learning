package fr.epita.platform.apps.timesheet.service;

import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.EntryRequest;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.ProjectDto;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.ProjectRequest;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.ReportRowDto;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.RowDto;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.WeekDto;
import fr.epita.platform.apps.timesheet.dto.TimesheetDtos.WeekSummaryDto;
import fr.epita.platform.apps.timesheet.entity.Project;
import fr.epita.platform.apps.timesheet.entity.TimeEntry;
import fr.epita.platform.apps.timesheet.entity.Timesheet;
import fr.epita.platform.apps.timesheet.entity.WeekStatus;
import fr.epita.platform.apps.timesheet.repository.ProjectRepository;
import fr.epita.platform.apps.timesheet.repository.TimesheetRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.CalendarRules;
import fr.epita.platform.common.IsoWeeks;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.ManagerRelations;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimesheetService {

    public static final String REPORTS = "timesheet_reports";
    static final BigDecimal MAX_PER_DAY = BigDecimal.TEN;
    static final int MISSING_LOOKBACK_WEEKS = 8;

    private final ProjectRepository projects;
    private final TimesheetRepository weeks;
    private final ManagerRelations relations;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- projets ----------

    @Transactional
    public List<ProjectDto> projects(String status) {
        ensureAbsenceProject();
        return projects.findAllByOrderByCodeAsc().stream().filter(p -> status == null || p.getStatus().equals(status))
                .map(TimesheetService::toDto).toList();
    }

    @Transactional
    public ProjectDto createProject(AuthUser me, ProjectRequest req) {
        me.requireAdmin();
        if (projects.existsByCodeIgnoreCase(req.code())) {
            throw ApiException.conflict("PROJECT_CODE_EXISTS", "Ce code de projet existe déjà");
        }
        Project p = new Project();
        apply(p, req);
        return toDto(projects.saveAndFlush(p));
    }

    @Transactional
    public ProjectDto updateProject(AuthUser me, UUID id, ProjectRequest req) {
        me.requireAdmin();
        Project p = projects.findById(id).orElseThrow(() -> ApiException.notFound("Projet"));
        if (projects.existsByCodeIgnoreCaseAndIdNot(req.code(), id)) {
            throw ApiException.conflict("PROJECT_CODE_EXISTS", "Ce code de projet existe déjà");
        }
        apply(p, req);
        return toDto(p);
    }

    // ---------- semaines ----------

    @Transactional(readOnly = true)
    public WeekDto get(AuthUser me, String week) {
        IsoWeeks.monday(week);
        return weeks.findByUserIdAndWeek(me.id(), week).map(t -> toDto(t, me)).orElseGet(() -> emptyWeek(me, week));
    }

    /** Remplace toutes les lignes de la semaine (brouillon ou renvoyée uniquement). */
    @Transactional
    public WeekDto save(AuthUser me, String week, List<EntryRequest> entries) {
        List<LocalDate> days = IsoWeeks.days(week);
        Timesheet t = weeks.findByUserIdAndWeek(me.id(), week).orElseGet(() -> {
            Timesheet n = new Timesheet();
            n.setUserId(me.id());
            n.setWeek(week);
            return n;
        });
        if (!t.getStatus().editable()) {
            throw ApiException.conflict("WEEK_LOCKED", "Cette semaine est soumise ou validée : elle n'est plus modifiable");
        }
        var errors = new ValidationException.Collector();
        Map<LocalDate, BigDecimal> perDay = new TreeMap<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < entries.size(); i++) {
            EntryRequest e = entries.get(i);
            errors.check(days.contains(e.date()), "entries[" + i + "].date", "doit être un jour de la semaine " + week);
            errors.check(e.hours().multiply(BigDecimal.valueOf(4)).stripTrailingZeros().scale() <= 0,
                    "entries[" + i + "].hours", "doit être un multiple de 0,25");
            errors.check(seen.add(e.projectId() + "|" + e.date()), "entries[" + i + "]", "une seule ligne par projet et par jour");
            perDay.merge(e.date(), e.hours(), BigDecimal::add);
        }
        perDay.forEach((d, h) -> errors.check(h.compareTo(MAX_PER_DAY) <= 0, "entries", "10 h maximum le " + d));
        errors.throwIfAny();
        Map<UUID, Project> byId = projects.findAllById(entries.stream().map(EntryRequest::projectId).distinct().toList())
                .stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        for (EntryRequest e : entries) {
            Project p = byId.get(e.projectId());
            if (p == null) {
                throw ApiException.notFound("Projet");
            }
            if (!p.isOpen()) {
                throw ApiException.conflict("PROJECT_CLOSED", "Le projet " + p.getCode() + " est clos");
            }
        }
        t.getEntries().clear();
        weeks.saveAndFlush(t);
        for (EntryRequest e : entries) {
            TimeEntry entry = new TimeEntry();
            entry.setTimesheet(t);
            entry.setProjectId(e.projectId());
            entry.setDate(e.date());
            entry.setHours(e.hours());
            t.getEntries().add(entry);
        }
        return toDto(weeks.saveAndFlush(t), me);
    }

    /** Chaque jour ouvré doit être rempli (une absence se déclare sur le projet ABSENCE). */
    @Transactional
    public WeekDto submit(AuthUser me, String week) {
        Timesheet t = weeks.findByUserIdAndWeek(me.id(), week).orElseThrow(() ->
                ApiException.conflict("WEEK_INCOMPLETE", "Aucune heure saisie pour cette semaine"));
        if (!t.getStatus().editable()) {
            throw ApiException.conflict("WEEK_LOCKED", "Cette semaine est déjà soumise ou validée");
        }
        List<LocalDate> missing = missingDays(t);
        if (!missing.isEmpty()) {
            throw ApiException.conflict("WEEK_INCOMPLETE", "Jours ouvrés sans saisie : " + missing);
        }
        t.setStatus(WeekStatus.SUBMITTED);
        t.setSubmittedAt(clock.instant());
        t.setComment(null);
        return toDto(t, me);
    }

    @Transactional(readOnly = true)
    public List<String> missing(AuthUser me) {
        LocalDate current = LocalDate.now(clock);
        Map<String, Timesheet> mine = weeks.findByUserId(me.id()).stream().collect(Collectors.toMap(Timesheet::getWeek, Function.identity()));
        List<String> out = new ArrayList<>();
        for (int i = MISSING_LOOKBACK_WEEKS; i >= 1; i--) {
            String w = IsoWeeks.of(current.minusWeeks(i));
            Timesheet t = mine.get(w);
            if (t == null || t.getStatus().editable()) {
                out.add(w);
            }
        }
        return out;
    }

    @Transactional(readOnly = true)
    public List<WeekSummaryDto> team(AuthUser me, WeekStatus status) {
        List<Timesheet> list = me.isAdmin() ? weeks.findAllByOrderByWeekDesc()
                : weeks.findByUserIdInOrderByWeekDesc(relations.employeesOf(REPORTS, me.id()));
        return list.stream().filter(t -> !t.getUserId().equals(me.id()))
                .filter(t -> status == null ? t.getStatus() != WeekStatus.DRAFT : t.getStatus() == status)
                .map(t -> new WeekSummaryDto(t.getWeek(), t.getUserId(), userLookup.name(t.getUserId()), t.getStatus(), total(t)))
                .toList();
    }

    @Transactional(readOnly = true)
    public WeekDto getFor(AuthUser me, String week, UUID userId) {
        requireManager(me, userId);
        Timesheet t = weeks.findByUserIdAndWeek(userId, week).orElseThrow(() -> ApiException.notFound("Semaine"));
        return toDto(t, me);
    }

    @Transactional
    public WeekDto approve(AuthUser me, String week, UUID userId) {
        Timesheet t = reviewable(me, week, userId);
        t.setStatus(WeekStatus.APPROVED);
        t.setReviewedBy(me.id());
        t.setReviewedAt(clock.instant());
        return toDto(t, me);
    }

    @Transactional
    public WeekDto sendBack(AuthUser me, String week, UUID userId, String comment) {
        if (comment == null || comment.isBlank()) {
            throw ValidationException.field("comment", "ne doit pas être vide");
        }
        Timesheet t = reviewable(me, week, userId);
        t.setStatus(WeekStatus.RETURNED);
        t.setComment(comment.trim());
        t.setReviewedBy(me.id());
        t.setReviewedAt(clock.instant());
        return toDto(t, me);
    }

    /** Heures soumises ou validées, par projet et par personne ; manager : ses salariés (et lui-même), ADMIN : tous. */
    @Transactional(readOnly = true)
    public List<ReportRowDto> report(AuthUser me, LocalDate from, LocalDate to, UUID projectId, UUID userId) {
        LocalDate start = from == null ? LocalDate.now(clock).withDayOfMonth(1) : from;
        LocalDate end = to == null ? start.plusMonths(1).minusDays(1) : to;
        if (end.isBefore(start)) {
            throw ValidationException.field("to", "doit être postérieur ou égal à from");
        }
        Set<UUID> scope = new HashSet<>();
        if (!me.isAdmin()) {
            scope.add(me.id());
            scope.addAll(relations.employeesOf(REPORTS, me.id()));
        }
        Map<UUID, Project> byId = projects.findAll().stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        Map<String, ReportRowDto> keys = new LinkedHashMap<>();
        for (Timesheet t : weeks.findAllByOrderByWeekDesc()) {
            if ((!me.isAdmin() && !scope.contains(t.getUserId())) || (userId != null && !userId.equals(t.getUserId()))
                    || !(t.getStatus() == WeekStatus.SUBMITTED || t.getStatus() == WeekStatus.APPROVED)) {
                continue;
            }
            for (TimeEntry e : t.getEntries()) {
                if (e.getDate().isBefore(start) || e.getDate().isAfter(end) || (projectId != null && !projectId.equals(e.getProjectId()))) {
                    continue;
                }
                Project p = byId.get(e.getProjectId());
                String key = p.getCode() + "|" + t.getUserId();
                totals.merge(key, e.getHours(), BigDecimal::add);
                keys.putIfAbsent(key, new ReportRowDto(p.getCode(), p.getName(), t.getUserId(), userLookup.name(t.getUserId()), null));
            }
        }
        return keys.entrySet().stream().map(k -> new ReportRowDto(k.getValue().projectCode(), k.getValue().projectName(),
                k.getValue().userId(), k.getValue().userName(), totals.get(k.getKey())))
                .sorted((a, b) -> (a.projectCode() + a.userName()).compareTo(b.projectCode() + b.userName())).toList();
    }

    // ---------- outils ----------

    private void ensureAbsenceProject() {
        if (!projects.existsByCodeIgnoreCase(Project.ABSENCE)) {
            Project p = new Project();
            p.setCode(Project.ABSENCE);
            p.setName("Absence (congés, maladie)");
            projects.saveAndFlush(p);
        }
    }

    private Timesheet reviewable(AuthUser me, String week, UUID userId) {
        requireManager(me, userId);
        Timesheet t = weeks.findByUserIdAndWeek(userId, week).orElseThrow(() -> ApiException.notFound("Semaine"));
        if (t.getStatus() != WeekStatus.SUBMITTED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Seule une semaine soumise peut être validée ou renvoyée");
        }
        return t;
    }

    private void requireManager(AuthUser me, UUID userId) {
        if (userId.equals(me.id())) {
            throw ApiException.forbidden("On ne valide pas sa propre semaine");
        }
        if (!me.isAdmin() && !relations.isManagerOf(REPORTS, me.id(), userId)) {
            throw ApiException.forbidden("Seul le manager du salarié valide ses semaines");
        }
    }

    private static List<LocalDate> missingDays(Timesheet t) {
        Set<LocalDate> filled = t.getEntries().stream().map(TimeEntry::getDate).collect(Collectors.toSet());
        return IsoWeeks.days(t.getWeek()).stream().filter(CalendarRules::isWorkingDay).filter(d -> !filled.contains(d)).toList();
    }

    private static BigDecimal total(Timesheet t) {
        return t.getEntries().stream().map(TimeEntry::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static void apply(Project p, ProjectRequest req) {
        p.setCode(req.code());
        p.setName(req.name().trim());
        p.setClient(req.client() == null || req.client().isBlank() ? null : req.client().trim());
        p.setStatus(req.status() == null ? "OPEN" : req.status());
    }

    private static ProjectDto toDto(Project p) {
        return new ProjectDto(p.getId(), p.getCode(), p.getName(), p.getClient(), p.getStatus());
    }

    private WeekDto emptyWeek(AuthUser me, String week) {
        Timesheet t = new Timesheet();
        t.setUserId(me.id());
        t.setWeek(week);
        return toDto(t, me);
    }

    private WeekDto toDto(Timesheet t, AuthUser me) {
        List<LocalDate> days = IsoWeeks.days(t.getWeek());
        Map<UUID, Project> byId = projects.findAllById(t.getEntries().stream().map(TimeEntry::getProjectId).distinct().toList())
                .stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        Map<UUID, Map<LocalDate, BigDecimal>> rows = new LinkedHashMap<>();
        Map<LocalDate, BigDecimal> dayTotals = new TreeMap<>();
        days.forEach(d -> dayTotals.put(d, BigDecimal.ZERO));
        for (TimeEntry e : t.getEntries()) {
            rows.computeIfAbsent(e.getProjectId(), k -> new TreeMap<>()).put(e.getDate(), e.getHours());
            dayTotals.merge(e.getDate(), e.getHours(), BigDecimal::add);
        }
        List<RowDto> rowDtos = rows.entrySet().stream().map(r -> {
            Project p = byId.get(r.getKey());
            return new RowDto(r.getKey(), p.getCode(), p.getName(), r.getValue(),
                    r.getValue().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add));
        }).sorted((a, b) -> a.projectCode().compareTo(b.projectCode())).toList();
        return new WeekDto(t.getWeek(), days.get(0), t.getUserId(), userLookup.name(t.getUserId()), t.getStatus(),
                t.getComment(), rowDtos, dayTotals, total(t), missingDays(t),
                t.getStatus().editable() && t.getUserId().equals(me.id()), t.getSubmittedAt(), userLookup.name(t.getReviewedBy()));
    }
}
