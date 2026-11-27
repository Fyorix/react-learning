package fr.epita.platform.apps.access.service;

import fr.epita.platform.apps.access.dto.AccessDtos.*;
import fr.epita.platform.apps.access.entity.*;
import fr.epita.platform.apps.access.repository.*;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validation en deux étapes (manager puis propriétaire de l'outil) ; une seule si le manager est le propriétaire,
 * jamais par le demandeur lui-même. Expiration appliquée à la lecture. Implémente {@link UserDeletionGuard} :
 * un utilisateur désactivé perd immédiatement tous ses accès.
 */
@Service
@RequiredArgsConstructor
public class AccessService implements UserDeletionGuard {

    public static final String REPORTS = "access_reports";
    static final int ADMIN_MAX_DAYS = 90;
    static final Duration REVIEW_AFTER = Duration.ofDays(180);
    private static final Set<AccessRequestStatus> PENDING = EnumSet.of(AccessRequestStatus.PENDING_MANAGER, AccessRequestStatus.PENDING_OWNER);

    private final ToolRepository tools;
    private final AccessRequestRepository requests;
    private final AccessGrantRepository grants;
    private final AuditEntryRepository audit;
    private final ManagerRelations relations;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- §4.4 : révocation à la désactivation ----------

    @Override
    public void beforeRemoval(User user) {
        for (AccessGrant g : grants.findByUserIdAndStatus(user.getId(), GrantStatus.ACTIVE)) {
            end(g, GrantStatus.REVOKED, AuditAction.REVOKED, null, "Compte désactivé");
        }
        for (AccessRequest r : requests.findByUserIdAndStatusIn(user.getId(), PENDING)) {
            r.setStatus(AccessRequestStatus.REJECTED);
            r.setReason("Compte désactivé");
            requests.save(r);
        }
        grants.flush();
    }

    // ---------- outils ----------

    @Transactional
    public List<ToolDto> tools(AuthUser me) {
        expire();
        return tools.findAllByOrderByNameAsc().stream().map(t -> toDto(me, t)).toList();
    }

    @Transactional
    public ToolDto addTool(AuthUser me, ToolRequest req) {
        me.requireAdmin();
        if (tools.existsByNameIgnoreCase(req.name().trim())) {
            throw ApiException.conflict("TOOL_EXISTS", "Cet outil existe déjà");
        }
        userLookup.require(req.ownerId());
        Tool t = new Tool();
        t.setName(req.name().trim());
        t.setOwnerId(req.ownerId());
        t.setLevels(new TreeSet<>(req.levels()).stream().map(Enum::name).collect(Collectors.joining(",")));
        return toDto(me, tools.saveAndFlush(t));
    }

    // ---------- demandes ----------

    @Transactional
    public AccessRequestDto request(AuthUser me, AccessRequestBody req) {
        expire();
        Tool t = tool(req.toolId());
        if (!t.levelList().contains(req.level())) {
            throw ValidationException.field("level", "niveau non proposé par cet outil");
        }
        if (req.level() == AccessLevel.ADMIN && req.durationDays() > ADMIN_MAX_DAYS) {
            throw ValidationException.field("durationDays", ADMIN_MAX_DAYS + " jours maximum pour le niveau ADMIN");
        }
        grants.findByToolIdAndUserIdAndStatus(t.getId(), me.id(), GrantStatus.ACTIVE).ifPresent(g -> {
            if (g.getLevel().ordinal() >= req.level().ordinal()) {
                throw ApiException.conflict("ALREADY_GRANTED", "Vous avez déjà un accès " + g.getLevel() + " à cet outil");
            }
        });
        if (requests.findByUserIdAndStatusIn(me.id(), PENDING).stream().anyMatch(r -> r.getToolId().equals(t.getId()))) {
            throw ApiException.conflict("REQUEST_PENDING", "Une demande est déjà en cours pour cet outil");
        }
        AccessRequest r = new AccessRequest();
        r.setToolId(t.getId());
        r.setUserId(me.id());
        r.setLevel(req.level());
        r.setJustification(req.justification().trim());
        r.setDurationDays(req.durationDays());
        UUID manager = relations.managerOf(REPORTS, me.id()).orElse(null);
        r.setManagerId(manager);
        // sans manager : directement chez le propriétaire
        r.setStatus(manager == null ? AccessRequestStatus.PENDING_OWNER : AccessRequestStatus.PENDING_MANAGER);
        return toDto(me, requests.saveAndFlush(r));
    }

    @Transactional(readOnly = true)
    public List<AccessRequestDto> mine(AuthUser me) {
        return requests.findByUserIdOrderByCreatedAtDesc(me.id()).stream().map(r -> toDto(me, r)).toList();
    }

    /** Demandes dont l'étape en cours m'attend : manager du demandeur, propriétaire de l'outil (ADMIN : toutes). */
    @Transactional(readOnly = true)
    public List<AccessRequestDto> toApprove(AuthUser me) {
        return requests.findByStatusInOrderByCreatedAtAsc(PENDING).stream().map(r -> toDto(me, r)).filter(AccessRequestDto::canApprove).toList();
    }

    @Transactional
    public AccessRequestDto approve(AuthUser me, UUID id) {
        AccessRequest r = requests.lock(id).orElseThrow(() -> ApiException.notFound("Demande"));
        Tool t = tool(r.getToolId());
        if (r.getUserId().equals(me.id())) {
            throw ApiException.conflict("SELF_APPROVAL", "Vous ne pouvez pas valider votre propre demande");
        }
        switch (r.getStatus()) {
            case PENDING_MANAGER -> {
                if (!me.isAdmin() && !me.id().equals(r.getManagerId())) {
                    throw ApiException.forbidden("Étape réservée au manager du demandeur");
                }
                // manager également propriétaire (ou ADMIN propriétaire) : une seule validation
                if (me.id().equals(t.getOwnerId())) {
                    grant(me, r, t);
                } else {
                    r.setStatus(AccessRequestStatus.PENDING_OWNER);
                }
            }
            case PENDING_OWNER -> {
                if (!me.isAdmin() && !me.id().equals(t.getOwnerId())) {
                    throw ApiException.forbidden("Étape réservée au propriétaire de l'outil");
                }
                grant(me, r, t);
            }
            default -> throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Demande déjà " + r.getStatus());
        }
        r.setDecidedBy(me.id());
        return toDto(me, requests.saveAndFlush(r));
    }

    @Transactional
    public AccessRequestDto reject(AuthUser me, UUID id, String reason) {
        AccessRequest r = requests.lock(id).orElseThrow(() -> ApiException.notFound("Demande"));
        Tool t = tool(r.getToolId());
        if (!PENDING.contains(r.getStatus())) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Demande déjà " + r.getStatus());
        }
        if (r.getUserId().equals(me.id())) {
            throw ApiException.conflict("SELF_APPROVAL", "Vous ne pouvez pas traiter votre propre demande");
        }
        boolean allowed = me.isAdmin() || (r.getStatus() == AccessRequestStatus.PENDING_MANAGER ? me.id().equals(r.getManagerId()) : me.id().equals(t.getOwnerId()));
        if (!allowed) {
            throw ApiException.forbidden("Étape réservée à un autre validateur");
        }
        r.setStatus(AccessRequestStatus.REJECTED);
        r.setReason(reason.trim());
        r.setDecidedBy(me.id());
        return toDto(me, requests.saveAndFlush(r));
    }

    /** Octroi : un niveau supérieur remplace l'accès actif existant. */
    private void grant(AuthUser me, AccessRequest r, Tool t) {
        grants.findByToolIdAndUserIdAndStatus(t.getId(), r.getUserId(), GrantStatus.ACTIVE).ifPresent(old -> {
            if (old.getLevel().ordinal() >= r.getLevel().ordinal()) {
                throw ApiException.conflict("ALREADY_GRANTED", "Un accès " + old.getLevel() + " est déjà actif");
            }
            end(old, GrantStatus.REPLACED, AuditAction.REPLACED, me.id(), "Remplacé par " + r.getLevel());
            grants.flush();
        });
        Instant now = clock.instant();
        AccessGrant g = new AccessGrant();
        g.setToolId(t.getId());
        g.setUserId(r.getUserId());
        g.setLevel(r.getLevel());
        g.setRequestId(r.getId());
        g.setExpiresAt(now.plus(Duration.ofDays(r.getDurationDays())));
        g.setStatus(GrantStatus.ACTIVE);
        g.setLastConfirmedAt(now);
        grants.saveAndFlush(g);
        log(AuditAction.GRANTED, g, me.id(), r.getDurationDays() + " jours");
        r.setStatus(AccessRequestStatus.GRANTED);
    }

    // ---------- accès ----------

    @Transactional
    public List<GrantDto> myGrants(AuthUser me) {
        expire();
        return grants.findByUserIdAndStatus(me.id(), GrantStatus.ACTIVE).stream().map(this::toDto)
                .sorted(Comparator.comparing(GrantDto::expiresAt)).toList();
    }

    @Transactional
    public List<GrantDto> toolGrants(AuthUser me, UUID toolId) {
        Tool t = tool(toolId);
        if (!me.isAdmin() && !me.id().equals(t.getOwnerId())) {
            throw ApiException.forbidden("Réservé au propriétaire de l'outil");
        }
        expire();
        return grants.findByToolIdAndStatusOrderByExpiresAtAsc(toolId, GrantStatus.ACTIVE).stream().map(this::toDto).toList();
    }

    @Transactional
    public GrantDto confirm(AuthUser me, UUID id) {
        AccessGrant g = activeGrant(id);
        Tool t = tool(g.getToolId());
        if (!me.isAdmin() && !me.id().equals(t.getOwnerId())) {
            throw ApiException.forbidden("Réservé au propriétaire de l'outil");
        }
        g.setLastConfirmedAt(clock.instant());
        grants.saveAndFlush(g);
        log(AuditAction.CONFIRMED, g, me.id(), null);
        return toDto(g);
    }

    @Transactional
    public void revoke(AuthUser me, UUID id) {
        AccessGrant g = activeGrant(id);
        Tool t = tool(g.getToolId());
        if (!me.isAdmin() && !me.id().equals(t.getOwnerId())) {
            throw ApiException.forbidden("Réservé au propriétaire de l'outil ou à un administrateur");
        }
        end(g, GrantStatus.REVOKED, AuditAction.REVOKED, me.id(), null);
    }

    @Transactional
    public List<AuditDto> audit(AuthUser me, UUID userId, UUID toolId) {
        me.requireAdmin();
        expire();
        Map<UUID, String> toolNames = tools.findAll().stream().collect(Collectors.toMap(Tool::getId, Tool::getName));
        return audit.findAllByOrderByCreatedAtDesc().stream()
                .filter(a -> (userId == null || a.getUserId().equals(userId)) && (toolId == null || a.getToolId().equals(toolId)))
                .map(a -> new AuditDto(a.getId(), a.getAction(), toolNames.get(a.getToolId()), userLookup.name(a.getUserId()), a.getLevel(),
                        a.getActorId() == null ? "système" : userLookup.name(a.getActorId()), a.getDetail(), a.getCreatedAt())).toList();
    }

    // ---------- outils internes ----------

    /** Expiration paresseuse des accès échus (journalisée). */
    private void expire() {
        Instant now = clock.instant();
        grants.findByStatus(GrantStatus.ACTIVE).stream().filter(g -> !now.isBefore(g.getExpiresAt()))
                .forEach(g -> end(g, GrantStatus.EXPIRED, AuditAction.EXPIRED, null, null));
    }

    private void end(AccessGrant g, GrantStatus status, AuditAction action, UUID actor, String detail) {
        g.setStatus(status);
        g.setEndedAt(clock.instant());
        grants.save(g);
        log(action, g, actor, detail);
    }

    private void log(AuditAction action, AccessGrant g, UUID actor, String detail) {
        AuditEntry a = new AuditEntry();
        a.setAction(action);
        a.setToolId(g.getToolId());
        a.setUserId(g.getUserId());
        a.setLevel(g.getLevel());
        a.setActorId(actor);
        a.setDetail(detail);
        audit.save(a);
    }

    private AccessGrant activeGrant(UUID id) {
        expire();
        AccessGrant g = grants.findById(id).orElseThrow(() -> ApiException.notFound("Accès"));
        if (g.getStatus() != GrantStatus.ACTIVE) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Accès déjà " + g.getStatus());
        }
        return g;
    }

    private Tool tool(UUID id) {
        return tools.findById(id).orElseThrow(() -> ApiException.notFound("Outil"));
    }

    private ToolDto toDto(AuthUser me, Tool t) {
        AccessLevel mine = grants.findByToolIdAndUserIdAndStatus(t.getId(), me.id(), GrantStatus.ACTIVE).map(AccessGrant::getLevel).orElse(null);
        return new ToolDto(t.getId(), t.getName(), t.getOwnerId(), userLookup.name(t.getOwnerId()), t.levelList(), mine);
    }

    private AccessRequestDto toDto(AuthUser me, AccessRequest r) {
        Tool t = tool(r.getToolId());
        boolean self = r.getUserId().equals(me.id());
        boolean canApprove = !self && switch (r.getStatus()) {
            case PENDING_MANAGER -> me.isAdmin() || me.id().equals(r.getManagerId());
            case PENDING_OWNER -> me.isAdmin() || me.id().equals(t.getOwnerId());
            default -> false;
        };
        return new AccessRequestDto(r.getId(), t.getId(), t.getName(), r.getUserId(), userLookup.name(r.getUserId()), r.getLevel(), r.getJustification(),
                r.getDurationDays(), r.getStatus(), r.getManagerId() == null ? null : userLookup.name(r.getManagerId()), userLookup.name(t.getOwnerId()),
                r.getReason(), r.getDecidedBy() == null ? null : userLookup.name(r.getDecidedBy()), r.getCreatedAt(), canApprove);
    }

    private GrantDto toDto(AccessGrant g) {
        return new GrantDto(g.getId(), g.getToolId(), tool(g.getToolId()).getName(), g.getUserId(), userLookup.name(g.getUserId()), g.getLevel(), g.getStatus(),
                g.getCreatedAt(), g.getExpiresAt(), g.getLastConfirmedAt(), g.getLastConfirmedAt().isBefore(clock.instant().minus(REVIEW_AFTER)));
    }
}
