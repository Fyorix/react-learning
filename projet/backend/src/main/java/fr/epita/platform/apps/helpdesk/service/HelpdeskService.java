package fr.epita.platform.apps.helpdesk.service;

import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.CommentDto;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.CommentRequest;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.StatsDto;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.TicketDto;
import fr.epita.platform.apps.helpdesk.dto.HelpdeskDtos.TicketRequest;
import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.Priority;
import fr.epita.platform.apps.helpdesk.entity.HelpdeskEnums.TicketStatus;
import fr.epita.platform.apps.helpdesk.entity.Ticket;
import fr.epita.platform.apps.helpdesk.entity.TicketComment;
import fr.epita.platform.apps.helpdesk.repository.TicketCommentRepository;
import fr.epita.platform.apps.helpdesk.repository.TicketRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.CalendarRules;
import fr.epita.platform.common.PageResponse;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HelpdeskService {

    public static final String ROLES = "helpdesk_roles";
    public static final String TECHNICIAN = "TECHNICIAN";
    static final Duration REOPEN_WINDOW = Duration.ofDays(7);

    private final TicketRepository tickets;
    private final TicketCommentRepository comments;
    private final AppRoles roles;
    private final UserLookup userLookup;
    private final Clock clock;

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(TECHNICIAN));
    }

    /** Délai attendu : 4 h, 1 jour, 3 jours, 5 jours ouvrés selon la priorité. */
    Instant dueAt(Priority p, Instant from) {
        return switch (p) {
            case CRITICAL -> from.plus(Duration.ofHours(4));
            case HIGH -> from.plus(Duration.ofDays(1));
            case NORMAL -> from.plus(Duration.ofDays(3));
            case LOW -> {
                ZonedDateTime t = from.atZone(clock.getZone());
                int added = 0;
                while (added < 5) {
                    t = t.plusDays(1);
                    if (CalendarRules.isWorkingDay(t.toLocalDate())) {
                        added++;
                    }
                }
                yield t.toInstant();
            }
        };
    }

    @Transactional
    public TicketDto open(AuthUser me, TicketRequest req) {
        Ticket t = new Ticket();
        t.setRequesterId(me.id());
        t.setTitle(req.title().trim());
        t.setDescription(req.description().trim());
        t.setCategory(req.category());
        t.setPriority(req.priority());
        Instant now = clock.instant();
        t.setCreatedAt(now);
        t.setDueAt(dueAt(req.priority(), now));
        t.setUpdatedAt(now);
        return toDto(tickets.saveAndFlush(t), me, true);
    }

    @Transactional
    public PageResponse<TicketDto> list(AuthUser me, TicketStatus status, Priority priority, String assignee,
                                        Boolean overdue, int page, int size) {
        boolean tech = isTech(me);
        List<Ticket> source = tech ? tickets.findAllByOrderByCreatedAtDesc() : tickets.findByRequesterIdOrderByCreatedAtDesc(me.id());
        source.forEach(this::autoClose);
        Instant now = clock.instant();
        UUID assigneeId = assignee == null || assignee.isBlank() ? null
                : "me".equals(assignee) ? me.id() : parse(assignee);
        List<TicketDto> list = source.stream()
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> priority == null || t.getPriority() == priority)
                .filter(t -> assigneeId == null || assigneeId.equals(t.getAssigneeId()))
                .filter(t -> overdue == null || isOverdue(t, now) == overdue)
                .map(t -> toDto(t, me, false)).toList();
        return PageResponse.ofList(list, page, size);
    }

    @Transactional
    public TicketDto get(AuthUser me, UUID id) {
        Ticket t = readable(me, id);
        autoClose(t);
        return toDto(t, me, true);
    }

    @Transactional
    public TicketDto comment(AuthUser me, UUID id, CommentRequest req) {
        Ticket t = readable(me, id);
        autoClose(t);
        boolean internal = Boolean.TRUE.equals(req.internal());
        if (internal && !isTech(me)) {
            throw ApiException.forbidden("Les commentaires internes sont réservés aux techniciens");
        }
        if (t.getStatus() == TicketStatus.CLOSED) {
            throw ApiException.conflict("TICKET_CLOSED", "Ce ticket est fermé");
        }
        addComment(t, me.id(), req.content(), internal);
        return toDto(t, me, true);
    }

    @Transactional
    public TicketDto assign(AuthUser me, UUID id, UUID technicianId) {
        roles.require(ROLES, me, TECHNICIAN);
        if (!me.isAdmin() && !technicianId.equals(me.id())) {
            throw ApiException.forbidden("Un technicien ne peut s'assigner qu'à lui-même");
        }
        var target = userLookup.require(technicianId);
        boolean targetIsTech = target.getRole() == fr.epita.platform.user.entity.Role.ADMIN
                || roles.hasExplicit(ROLES, technicianId, TECHNICIAN);
        if (!targetIsTech) {
            throw ValidationException.field("technicianId", "doit désigner un technicien");
        }
        Ticket t = find(id);
        if (t.getStatus() == TicketStatus.CLOSED) {
            throw ApiException.conflict("TICKET_CLOSED", "Ce ticket est fermé");
        }
        t.setAssigneeId(technicianId);
        t.setUpdatedAt(clock.instant());
        return toDto(t, me, true);
    }

    /** OPEN → IN_PROGRESS → RESOLVED → CLOSED, par le technicien assigné ou un ADMIN. */
    @Transactional
    public TicketDto changeStatus(AuthUser me, UUID id, TicketStatus target, String comment) {
        Ticket t = find(id);
        autoClose(t);
        if (!me.isAdmin() && !me.id().equals(t.getAssigneeId())) {
            throw ApiException.forbidden("Seul le technicien assigné change le statut");
        }
        TicketStatus from = t.getStatus();
        boolean allowed = (from == TicketStatus.OPEN && target == TicketStatus.IN_PROGRESS)
                || (from == TicketStatus.IN_PROGRESS && target == TicketStatus.RESOLVED)
                || (from == TicketStatus.RESOLVED && target == TicketStatus.CLOSED);
        if (!allowed) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Transition " + from + " → " + target + " interdite");
        }
        Instant now = clock.instant();
        t.setStatus(target);
        if (target == TicketStatus.RESOLVED) {
            t.setResolvedAt(now);
        }
        if (target == TicketStatus.CLOSED) {
            t.setClosedAt(now);
        }
        t.setUpdatedAt(now);
        if (comment != null && !comment.isBlank()) {
            addComment(t, me.id(), comment, false);
        }
        return toDto(t, me, true);
    }

    /** Le demandeur rouvre un ticket résolu pendant 7 jours ; il repasse en cours de traitement. */
    @Transactional
    public TicketDto reopen(AuthUser me, UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw ValidationException.field("reason", "ne doit pas être vide");
        }
        Ticket t = find(id);
        if (!t.getRequesterId().equals(me.id())) {
            throw ApiException.forbidden("Seul le demandeur peut rouvrir son ticket");
        }
        autoClose(t);
        if (t.getStatus() == TicketStatus.CLOSED) {
            throw ApiException.conflict("REOPEN_EXPIRED", "Le délai de réouverture de 7 jours est dépassé");
        }
        if (t.getStatus() != TicketStatus.RESOLVED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Seul un ticket résolu peut être rouvert");
        }
        t.setStatus(TicketStatus.IN_PROGRESS);
        t.setResolvedAt(null);
        t.setUpdatedAt(clock.instant());
        addComment(t, me.id(), "Réouverture : " + reason.trim(), false);
        return toDto(t, me, true);
    }

    @Transactional
    public StatsDto stats(AuthUser me) {
        roles.require(ROLES, me, TECHNICIAN);
        List<Ticket> all = tickets.findAllByOrderByCreatedAtDesc();
        all.forEach(this::autoClose);
        Instant now = clock.instant();
        Map<TicketStatus, Long> byStatus = new EnumMap<>(TicketStatus.class);
        for (TicketStatus s : TicketStatus.values()) {
            byStatus.put(s, all.stream().filter(t -> t.getStatus() == s).count());
        }
        Map<String, Long> byTech = new TreeMap<>();
        all.stream().filter(t -> t.getAssigneeId() != null && (t.getStatus() == TicketStatus.OPEN || t.getStatus() == TicketStatus.IN_PROGRESS))
                .forEach(t -> byTech.merge(userLookup.name(t.getAssigneeId()), 1L, Long::sum));
        long overdue = all.stream().filter(t -> isOverdue(t, now)).count();
        var durations = all.stream().filter(t -> t.getResolvedAt() != null)
                .mapToLong(t -> Duration.between(t.getCreatedAt(), t.getResolvedAt()).toMinutes()).summaryStatistics();
        Double avg = durations.getCount() == 0 ? null : Math.round(durations.getAverage() / 6.0) / 10.0;
        return new StatsDto(byStatus, byTech, overdue, avg);
    }

    // ---------- outils ----------

    /** Un ticket résolu depuis plus de 7 jours est fermé automatiquement. */
    private void autoClose(Ticket t) {
        if (t.getStatus() == TicketStatus.RESOLVED && t.getResolvedAt() != null
                && t.getResolvedAt().plus(REOPEN_WINDOW).isBefore(clock.instant())) {
            t.setStatus(TicketStatus.CLOSED);
            t.setClosedAt(t.getResolvedAt().plus(REOPEN_WINDOW));
        }
    }

    static boolean isOverdue(Ticket t, Instant now) {
        return (t.getStatus() == TicketStatus.OPEN || t.getStatus() == TicketStatus.IN_PROGRESS) && t.getDueAt().isBefore(now);
    }

    private void addComment(Ticket t, UUID author, String content, boolean internal) {
        TicketComment c = new TicketComment();
        c.setTicketId(t.getId());
        c.setAuthorId(author);
        c.setContent(content.trim());
        c.setInternal(internal);
        comments.saveAndFlush(c);
        t.setUpdatedAt(clock.instant());
    }

    private boolean isTech(AuthUser me) {
        return roles.has(ROLES, me, TECHNICIAN);
    }

    private Ticket readable(AuthUser me, UUID id) {
        Ticket t = find(id);
        if (!isTech(me) && !t.getRequesterId().equals(me.id())) {
            throw ApiException.notFound("Ticket");
        }
        return t;
    }

    private Ticket find(UUID id) {
        return tickets.findById(id).orElseThrow(() -> ApiException.notFound("Ticket"));
    }

    private static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw ValidationException.field("assignee", "doit être un identifiant ou « me »");
        }
    }

    private TicketDto toDto(Ticket t, AuthUser me, boolean withComments) {
        boolean tech = isTech(me);
        List<CommentDto> list = !withComments ? List.of() : comments.findByTicketIdOrderByCreatedAtAsc(t.getId()).stream()
                .filter(c -> tech || !c.isInternal())
                .map(c -> new CommentDto(c.getId(), c.getAuthorId(), userLookup.name(c.getAuthorId()), c.getContent(),
                        c.isInternal(), c.getCreatedAt())).toList();
        Instant now = clock.instant();
        boolean canReopen = t.getRequesterId().equals(me.id()) && t.getStatus() == TicketStatus.RESOLVED;
        return new TicketDto(t.getId(), t.getTitle(), t.getDescription(), t.getCategory(), t.getPriority(), t.getStatus(),
                t.getRequesterId(), userLookup.name(t.getRequesterId()), t.getAssigneeId(), userLookup.name(t.getAssigneeId()),
                t.getDueAt(), isOverdue(t, now), t.getResolvedAt(), t.getClosedAt(), canReopen, t.getCreatedAt(),
                t.getUpdatedAt(), list);
    }
}
