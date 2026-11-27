package fr.epita.platform.apps.crm.service;

import fr.epita.platform.apps.crm.dto.CrmDtos.*;
import fr.epita.platform.apps.crm.entity.*;
import fr.epita.platform.apps.crm.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pipeline commercial : une opportunité avance ou recule d'une étape, ou se clôt (WON / LOST, motif obligatoire)
 * depuis n'importe quelle étape ; une fois close, elle n'est plus modifiable. Seul son commercial (ou un ADMIN) la modifie.
 */
@Service
@RequiredArgsConstructor
public class CrmService {

    public static final String ROLES = "crm_roles";
    public static final String SALES = "SALES";
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    private static final List<PipelineStage> OPEN = List.of(PipelineStage.QUALIFICATION, PipelineStage.PROPOSAL, PipelineStage.NEGOTIATION);

    private final CrmAccountRepository accounts;
    private final CrmContactRepository contacts;
    private final OpportunityRepository opportunities;
    private final InteractionRepository interactions;
    private final FollowUpTaskRepository tasks;
    private final AppRoles roles;
    private final UserLookup userLookup;
    private final Clock clock;

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(SALES));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    // ---------- comptes ----------

    @Transactional(readOnly = true)
    public List<AccountSummary> accounts(AuthUser me, String search, UUID ownerId) {
        sales(me);
        String term = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        return accounts.findAllByOrderByNameAsc().stream()
                .filter(a -> term == null || a.getName().toLowerCase(Locale.ROOT).contains(term) || a.getIndustry().toLowerCase(Locale.ROOT).contains(term))
                .filter(a -> ownerId == null || a.getOwnerId().equals(ownerId))
                .map(a -> {
                    List<Opportunity> open = opportunities.findByAccountIdOrderByExpectedCloseDateAsc(a.getId()).stream().filter(o -> !o.getStage().closed()).toList();
                    return new AccountSummary(a.getId(), a.getName(), a.getIndustry(), a.getSize(), a.getOwnerId(), userLookup.name(a.getOwnerId()), open.size(),
                            open.stream().map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
                }).toList();
    }

    @Transactional
    public AccountDto createAccount(AuthUser me, AccountRequest req) {
        sales(me);
        CrmAccount a = new CrmAccount();
        applyAccount(me, a, req);
        return account(me, accounts.saveAndFlush(a).getId());
    }

    @Transactional
    public AccountDto updateAccount(AuthUser me, UUID id, AccountRequest req) {
        CrmAccount a = accountEntity(me, id);
        if (!me.isAdmin() && !a.getOwnerId().equals(me.id())) {
            throw ApiException.forbidden("Réservé au commercial responsable du compte");
        }
        applyAccount(me, a, req);
        accounts.saveAndFlush(a);
        return account(me, id);
    }

    private void applyAccount(AuthUser me, CrmAccount a, AccountRequest req) {
        UUID owner = req.ownerId() == null ? (a.getOwnerId() == null ? me.id() : a.getOwnerId()) : req.ownerId();
        // attribuer un compte à un autre commercial : ADMIN seulement, vers un utilisateur SALES
        if (!owner.equals(me.id()) && !owner.equals(a.getOwnerId())) {
            if (!me.isAdmin()) {
                throw ApiException.forbidden("Seul un ADMIN attribue un compte à un autre commercial");
            }
            if (!roles.hasExplicit(ROLES, owner, SALES)) {
                throw ValidationException.field("ownerId", "le responsable doit avoir le rôle SALES");
            }
        }
        a.setName(req.name().trim());
        a.setIndustry(req.industry().trim());
        a.setSize(req.size());
        a.setOwnerId(owner);
    }

    @Transactional(readOnly = true)
    public AccountDto account(AuthUser me, UUID id) {
        CrmAccount a = accountEntity(me, id);
        List<ContactDto> cs = contacts.findByAccountIdOrderByLastNameAscFirstNameAsc(id).stream()
                .map(c -> new ContactDto(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(), c.getPhone(), c.getRole())).toList();
        List<OpportunityDto> os = opportunities.findByAccountIdOrderByExpectedCloseDateAsc(id).stream().map(o -> toDto(me, o)).toList();
        List<InteractionDto> is = interactions.findByAccountIdOrderByDateDescCreatedAtDesc(id).stream().map(this::toDto).toList();
        return new AccountDto(a.getId(), a.getName(), a.getIndustry(), a.getSize(), a.getOwnerId(), userLookup.name(a.getOwnerId()),
                me.isAdmin() || a.getOwnerId().equals(me.id()), cs, os, is);
    }

    @Transactional
    public ContactDto addContact(AuthUser me, UUID accountId, ContactRequest req) {
        accountEntity(me, accountId);
        String email = req.email().trim().toLowerCase(Locale.ROOT);
        if (contacts.existsByAccountIdAndEmail(accountId, email)) {
            throw ApiException.conflict("CONTACT_EXISTS", "Un contact de ce compte a déjà cet e-mail");
        }
        CrmContact c = new CrmContact();
        c.setAccountId(accountId);
        c.setFirstName(req.firstName().trim());
        c.setLastName(req.lastName().trim());
        c.setEmail(email);
        c.setPhone(req.phone() == null || req.phone().isBlank() ? null : req.phone().trim());
        c.setRole(req.role().trim());
        try {
            contacts.saveAndFlush(c);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("CONTACT_EXISTS", "Un contact de ce compte a déjà cet e-mail");
        }
        return new ContactDto(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(), c.getPhone(), c.getRole());
    }

    // ---------- opportunités ----------

    @Transactional
    public OpportunityDto createOpportunity(AuthUser me, OpportunityRequest req) {
        accountEntity(me, req.accountId());
        Opportunity o = new Opportunity();
        o.setAccountId(req.accountId());
        o.setOwnerId(me.id());
        o.setTitle(req.title().trim());
        o.setAmount(req.amount());
        o.setExpectedCloseDate(req.expectedCloseDate());
        o.setStage(PipelineStage.QUALIFICATION);
        return toDto(me, opportunities.saveAndFlush(o));
    }

    @Transactional(readOnly = true)
    public List<StageColumn> pipeline(AuthUser me, PipelineStage stage, UUID ownerId) {
        sales(me);
        List<Opportunity> all = opportunities.findAllByOrderByExpectedCloseDateAsc().stream()
                .filter(o -> ownerId == null || o.getOwnerId().equals(ownerId)).toList();
        return Arrays.stream(PipelineStage.values()).filter(s -> stage == null || s == stage).map(s -> {
            List<OpportunityDto> list = all.stream().filter(o -> o.getStage() == s).map(o -> toDto(me, o)).toList();
            return new StageColumn(s, s.probability(), list, list.stream().map(OpportunityDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                    list.stream().map(OpportunityDto::weighted).reduce(BigDecimal.ZERO, BigDecimal::add));
        }).toList();
    }

    @Transactional(readOnly = true)
    public OpportunityDetailDto opportunity(AuthUser me, UUID id) {
        sales(me);
        Opportunity o = opportunities.findById(id).orElseThrow(() -> ApiException.notFound("Opportunité"));
        return new OpportunityDetailDto(toDto(me, o), interactions.findByOpportunityIdOrderByDateDescCreatedAtDesc(id).stream().map(this::toDto).toList(), allowed(o.getStage()));
    }

    @Transactional
    public OpportunityDto updateOpportunity(AuthUser me, UUID id, OpportunityRequest req) {
        Opportunity o = editable(me, id);
        if (!o.getAccountId().equals(req.accountId())) {
            accountEntity(me, req.accountId());
        }
        o.setAccountId(req.accountId());
        o.setTitle(req.title().trim());
        o.setAmount(req.amount());
        o.setExpectedCloseDate(req.expectedCloseDate());
        return toDto(me, opportunities.saveAndFlush(o));
    }

    @Transactional
    public OpportunityDto changeStage(AuthUser me, UUID id, StageRequest req) {
        Opportunity o = editable(me, id);
        if (!allowed(o.getStage()).contains(req.stage())) {
            throw ApiException.conflict("INVALID_STAGE_TRANSITION", "Passage de " + o.getStage() + " à " + req.stage() + " impossible : une étape à la fois, ou clôture");
        }
        if (req.stage() == PipelineStage.LOST && (req.lostReason() == null || req.lostReason().isBlank())) {
            throw ValidationException.field("lostReason", "motif obligatoire pour une opportunité perdue");
        }
        o.setStage(req.stage());
        o.setLostReason(req.stage() == PipelineStage.LOST ? req.lostReason().trim() : null);
        if (req.stage().closed()) {
            o.setClosedAt(clock.instant());
        }
        return toDto(me, opportunities.saveAndFlush(o));
    }

    /** Étapes accessibles : voisines dans le pipeline, ou clôture. */
    static List<PipelineStage> allowed(PipelineStage current) {
        if (current.closed()) {
            return List.of();
        }
        int i = OPEN.indexOf(current);
        List<PipelineStage> out = new ArrayList<>();
        if (i > 0) {
            out.add(OPEN.get(i - 1));
        }
        if (i < OPEN.size() - 1) {
            out.add(OPEN.get(i + 1));
        }
        out.add(PipelineStage.WON);
        out.add(PipelineStage.LOST);
        return out;
    }

    private Opportunity editable(AuthUser me, UUID id) {
        sales(me);
        Opportunity o = opportunities.lock(id).orElseThrow(() -> ApiException.notFound("Opportunité"));
        if (!me.isAdmin() && !o.getOwnerId().equals(me.id())) {
            throw ApiException.forbidden("Réservé au commercial de l'opportunité");
        }
        if (o.getStage().closed()) {
            throw ApiException.conflict("OPPORTUNITY_CLOSED", "Opportunité close (" + o.getStage() + ") : plus modifiable");
        }
        return o;
    }

    // ---------- interactions et relances ----------

    @Transactional
    public InteractionDto addInteraction(AuthUser me, InteractionRequest req) {
        sales(me);
        if (req.accountId() == null && req.opportunityId() == null) {
            throw ValidationException.field("accountId", "un compte ou une opportunité est requis");
        }
        if (req.date().isAfter(today())) {
            throw ValidationException.field("date", "ne doit pas être dans le futur");
        }
        UUID accountId = req.accountId();
        if (req.opportunityId() != null) {
            Opportunity o = opportunities.findById(req.opportunityId()).orElseThrow(() -> ApiException.notFound("Opportunité"));
            if (accountId != null && !accountId.equals(o.getAccountId())) {
                throw ValidationException.field("opportunityId", "l'opportunité n'appartient pas à ce compte");
            }
            accountId = o.getAccountId();
        }
        accountEntity(me, accountId);
        Interaction i = new Interaction();
        i.setAccountId(accountId);
        i.setOpportunityId(req.opportunityId());
        i.setType(req.type());
        i.setDate(req.date());
        i.setSummary(req.summary().trim());
        i.setAuthorId(me.id());
        return toDto(interactions.saveAndFlush(i));
    }

    @Transactional(readOnly = true)
    public List<TaskDto> myTasks(AuthUser me, String due) {
        sales(me);
        LocalDate today = today();
        return tasks.findByOwnerIdOrderByDueDateAsc(me.id()).stream().filter(t -> switch (due == null ? "all" : due) {
            case "today" -> !t.isDone() && t.getDueDate().equals(today);
            case "overdue" -> !t.isDone() && t.getDueDate().isBefore(today);
            case "week" -> !t.isDone() && !t.getDueDate().isBefore(today) && !t.getDueDate().isAfter(today.plusDays(6));
            case "all" -> true;
            default -> throw ValidationException.field("due", "today, overdue ou week");
        }).map(this::toDto).toList();
    }

    @Transactional
    public TaskDto createTask(AuthUser me, TaskRequest req) {
        sales(me);
        UUID accountId = req.accountId();
        if (req.opportunityId() != null) {
            Opportunity o = opportunities.findById(req.opportunityId()).orElseThrow(() -> ApiException.notFound("Opportunité"));
            accountId = o.getAccountId();
        }
        if (accountId != null) {
            accountEntity(me, accountId);
        }
        FollowUpTask t = new FollowUpTask();
        t.setOwnerId(me.id());
        t.setAccountId(accountId);
        t.setOpportunityId(req.opportunityId());
        t.setDueDate(req.dueDate());
        t.setLabel(req.label().trim());
        t.setDone(false);
        return toDto(tasks.saveAndFlush(t));
    }

    @Transactional
    public TaskDto patchTask(AuthUser me, UUID id, boolean done) {
        FollowUpTask t = tasks.findById(id).filter(x -> x.getOwnerId().equals(me.id())).orElseThrow(() -> ApiException.notFound("Tâche"));
        t.setDone(done);
        return toDto(tasks.saveAndFlush(t));
    }

    /** Commerciaux actifs (rôle SALES explicite), pour les filtres et l'attribution des comptes. */
    @Transactional(readOnly = true)
    public List<UUID> salesPeople(AuthUser me) {
        sales(me);
        return roles.usersWith(ROLES, SALES);
    }

    // ---------- prévisions ----------

    /** Somme des montants × probabilité des opportunités ouvertes, par mois de signature prévue. */
    @Transactional(readOnly = true)
    public ForecastDto forecast(AuthUser me, int months, UUID ownerId) {
        sales(me);
        if (months < 1 || months > 24) {
            throw ValidationException.field("months", "de 1 à 24");
        }
        YearMonth start = YearMonth.from(today());
        List<Opportunity> open = opportunities.findAllByOrderByExpectedCloseDateAsc().stream()
                .filter(o -> !o.getStage().closed() && (ownerId == null || o.getOwnerId().equals(ownerId))).toList();
        List<ForecastMonth> list = new ArrayList<>();
        for (int i = 0; i < months; i++) {
            YearMonth m = start.plusMonths(i);
            List<Opportunity> in = open.stream().filter(o -> YearMonth.from(o.getExpectedCloseDate()).equals(m)).toList();
            list.add(new ForecastMonth(m.toString(), in.stream().map(CrmService::weighted).reduce(BigDecimal.ZERO, BigDecimal::add),
                    in.stream().map(Opportunity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add), in.size()));
        }
        BigDecimal overdue = open.stream().filter(o -> YearMonth.from(o.getExpectedCloseDate()).isBefore(start)).map(CrmService::weighted).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ForecastDto(list, list.stream().map(ForecastMonth::weighted).reduce(BigDecimal.ZERO, BigDecimal::add), overdue);
    }

    static BigDecimal weighted(Opportunity o) {
        return o.getAmount().multiply(BigDecimal.valueOf(o.getStage().probability())).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    // ---------- outils ----------

    private void sales(AuthUser me) {
        roles.require(ROLES, me, SALES);
    }

    private CrmAccount accountEntity(AuthUser me, UUID id) {
        sales(me);
        return accounts.findById(id).orElseThrow(() -> ApiException.notFound("Compte"));
    }

    private OpportunityDto toDto(AuthUser me, Opportunity o) {
        String account = accounts.findById(o.getAccountId()).map(CrmAccount::getName).orElse(null);
        return new OpportunityDto(o.getId(), o.getAccountId(), account, o.getOwnerId(), userLookup.name(o.getOwnerId()), o.getTitle(), o.getAmount(),
                o.getExpectedCloseDate(), o.getStage(), o.getStage().probability(), weighted(o), o.getLostReason(), o.getClosedAt(),
                !o.getStage().closed() && (me.isAdmin() || o.getOwnerId().equals(me.id())));
    }

    private InteractionDto toDto(Interaction i) {
        String title = i.getOpportunityId() == null ? null : opportunities.findById(i.getOpportunityId()).map(Opportunity::getTitle).orElse(null);
        return new InteractionDto(i.getId(), i.getAccountId(), i.getOpportunityId(), title, i.getType(), i.getDate(), i.getSummary(),
                i.getAuthorId() == null ? null : userLookup.name(i.getAuthorId()));
    }

    private TaskDto toDto(FollowUpTask t) {
        String account = t.getAccountId() == null ? null : accounts.findById(t.getAccountId()).map(CrmAccount::getName).orElse(null);
        String opp = t.getOpportunityId() == null ? null : opportunities.findById(t.getOpportunityId()).map(Opportunity::getTitle).orElse(null);
        return new TaskDto(t.getId(), t.getDueDate(), t.getLabel(), t.isDone(), !t.isDone() && t.getDueDate().isBefore(today()), t.getAccountId(), account, t.getOpportunityId(), opp);
    }
}
