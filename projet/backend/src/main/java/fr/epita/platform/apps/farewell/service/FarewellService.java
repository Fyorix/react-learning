package fr.epita.platform.apps.farewell.service;

import fr.epita.platform.apps.farewell.dto.FarewellDtos.*;
import fr.epita.platform.apps.farewell.entity.Contribution;
import fr.epita.platform.apps.farewell.entity.Pot;
import fr.epita.platform.apps.farewell.entity.PotStatus;
import fr.epita.platform.apps.farewell.repository.ContributionRepository;
import fr.epita.platform.apps.farewell.repository.PotRepository;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Visibilité : brouillon = organisateur ; ouverte = organisateur et invités ; close = + bénéficiaire.
 * Tous les autres (y compris le bénéficiaire avant la clôture) reçoivent 404 ; un ADMIN voit tout.
 * Clôture automatique « paresseuse » : toute lecture d'une cagnotte échue la clôture.
 */
@Service
@RequiredArgsConstructor
public class FarewellService {

    static final Duration MAX_DURATION = Duration.ofDays(60);

    private final PotRepository pots;
    private final ContributionRepository contributions;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- wizard ----------

    @Transactional
    public PotDto create(AuthUser me, IdentityStep req) {
        Pot p = new Pot();
        p.setOrganizerId(me.id());
        p.setStatus(PotStatus.DRAFT);
        applyIdentity(me, p, req);
        return toDto(me, pots.saveAndFlush(p));
    }

    @Transactional
    public PotDto identity(AuthUser me, UUID id, IdentityStep req) {
        Pot p = draft(me, id);
        applyIdentity(me, p, req);
        return toDto(me, pots.saveAndFlush(p));
    }

    @Transactional
    public PotDto goal(AuthUser me, UUID id, GoalStep req) {
        Pot p = draft(me, id);
        List<String> errors = closesAtErrors(req.closesAt());
        if (!errors.isEmpty()) {
            throw ValidationException.field("closesAt", errors.get(0));
        }
        p.setGoal(req.goal());
        p.setClosesAt(req.closesAt());
        return toDto(me, pots.saveAndFlush(p));
    }

    @Transactional
    public PotDto invitees(AuthUser me, UUID id, InviteesStep req) {
        Pot p = draft(me, id);
        var errors = new ValidationException.Collector();
        for (int i = 0; i < req.inviteeIds().size(); i++) {
            UUID u = req.inviteeIds().get(i);
            errors.check(!u.equals(p.getBeneficiaryId()), "inviteeIds[" + i + "]", "le bénéficiaire ne peut pas être invité");
            errors.check(!u.equals(p.getOrganizerId()), "inviteeIds[" + i + "]", "l'organisateur participe d'office");
        }
        errors.throwIfAny();
        req.inviteeIds().forEach(userLookup::require);
        p.setInviteeIds(new HashSet<>(req.inviteeIds()));
        p.setInviteesSet(true);
        return toDto(me, pots.saveAndFlush(p));
    }

    @Transactional
    public ReviewDto review(AuthUser me, UUID id) {
        Pot p = draft(me, id);
        List<StepStatus> steps = steps(p);
        return new ReviewDto(toDto(me, p), steps, steps.stream().allMatch(StepStatus::valid));
    }

    @Transactional
    public PotDto open(AuthUser me, UUID id) {
        Pot p = draft(me, id);
        List<StepStatus> steps = steps(p);
        if (!steps.stream().allMatch(StepStatus::valid)) {
            List<String> invalid = steps.stream().filter(s -> !s.valid()).map(s -> s.step() + " (" + s.name() + ")").toList();
            throw ApiException.conflict("POT_INCOMPLETE", "Étapes incomplètes : " + String.join(", ", invalid));
        }
        p.setStatus(PotStatus.OPEN);
        return toDto(me, pots.saveAndFlush(p));
    }

    /** Chaque étape est revalidée au moment de la publication (une date de clôture peut être devenue passée). */
    private List<StepStatus> steps(Pot p) {
        List<String> e1 = new ArrayList<>();
        if (p.getBeneficiaryId().equals(p.getOrganizerId())) {
            e1.add("le bénéficiaire ne peut pas être l'organisateur");
        }
        List<String> e2 = p.getClosesAt() == null ? List.of("date de clôture manquante") : closesAtErrors(p.getClosesAt());
        List<String> e3 = new ArrayList<>();
        if (!p.isInviteesSet() || p.getInviteeIds().isEmpty()) {
            e3.add("au moins un invité");
        }
        if (p.getInviteeIds().contains(p.getBeneficiaryId())) {
            e3.add("le bénéficiaire ne peut pas être invité");
        }
        return List.of(new StepStatus(1, "Bénéficiaire", e1.isEmpty(), e1), new StepStatus(2, "Objectif et clôture", e2.isEmpty(), e2),
                new StepStatus(3, "Invités", e3.isEmpty(), e3));
    }

    private List<String> closesAtErrors(java.time.Instant closesAt) {
        var now = clock.instant();
        if (!closesAt.isAfter(now)) {
            return List.of("doit être dans le futur");
        }
        if (closesAt.isAfter(now.plus(MAX_DURATION))) {
            return List.of("60 jours maximum");
        }
        return List.of();
    }

    private void applyIdentity(AuthUser me, Pot p, IdentityStep req) {
        if (req.beneficiaryId().equals(me.id())) {
            throw ValidationException.field("beneficiaryId", "le bénéficiaire ne peut pas être l'organisateur");
        }
        if (p.getInviteeIds().contains(req.beneficiaryId())) {
            throw ValidationException.field("beneficiaryId", "le bénéficiaire fait partie des invités");
        }
        userLookup.require(req.beneficiaryId());
        p.setBeneficiaryId(req.beneficiaryId());
        p.setOccasion(req.occasion());
        p.setTitle(req.title().trim());
    }

    // ---------- consultation ----------

    @Transactional
    public List<PotDto> mine(AuthUser me) {
        return pots.involving(me.id()).stream().filter(p -> visible(me, refresh(p), false)).map(p -> toDto(me, p)).toList();
    }

    @Transactional
    public PotDto get(AuthUser me, UUID id) {
        return toDto(me, visibleOr404(me, id));
    }

    @Transactional
    public CardDto card(AuthUser me, UUID id) {
        Pot p = visibleOr404(me, id);
        List<Contribution> cs = contributions.findByPotIdOrderByCreatedAtAsc(id);
        Map<UUID, String> names = userLookup.namesOf(cs, Contribution::getUserId);
        List<CardMessage> messages = cs.stream().filter(c -> c.getMessage() != null)
                .map(c -> new CardMessage(names.get(c.getUserId()), c.getMessage(), c.getUpdatedAt())).toList();
        return new CardDto(id, p.getTitle(), userLookup.name(p.getBeneficiaryId()), p.getOccasion(), p.getStatus(), cs.size(), messages);
    }

    // ---------- participation ----------

    @Transactional
    public PotDto contribute(AuthUser me, UUID id, ContributionRequest req) {
        Pot p = openForContributions(me, id);
        Contribution c = contributions.findByPotIdAndUserId(id, me.id()).orElseGet(() -> {
            Contribution n = new Contribution();
            n.setPotId(id);
            n.setUserId(me.id());
            return n;
        });
        c.setAmount(req.amount().setScale(2, RoundingMode.HALF_UP));
        c.setMessage(req.message() == null || req.message().isBlank() ? null : req.message().trim());
        c.setUpdatedAt(clock.instant());
        contributions.saveAndFlush(c);
        return toDto(me, p);
    }

    @Transactional
    public PotDto withdraw(AuthUser me, UUID id) {
        Pot p = openForContributions(me, id);
        Contribution c = contributions.findByPotIdAndUserId(id, me.id()).orElseThrow(() -> ApiException.notFound("Participation"));
        contributions.delete(c);
        contributions.flush();
        return toDto(me, p);
    }

    private Pot openForContributions(AuthUser me, UUID id) {
        Pot p = pots.lock(id).orElseThrow(() -> ApiException.notFound("Cagnotte"));
        refresh(p);
        if (!visible(me, p, false) || p.getBeneficiaryId().equals(me.id())) {
            throw ApiException.notFound("Cagnotte");
        }
        if (p.getStatus() != PotStatus.OPEN) {
            throw ApiException.conflict("POT_CLOSED", p.getStatus() == PotStatus.DRAFT ? "La cagnotte n'est pas encore publiée" : "La cagnotte est close");
        }
        return p;
    }

    // ---------- organisateur ----------

    @Transactional
    public PotDto close(AuthUser me, UUID id) {
        Pot p = pots.lock(id).orElseThrow(() -> ApiException.notFound("Cagnotte"));
        refresh(p);
        if (!visible(me, p, true)) {
            throw ApiException.notFound("Cagnotte");
        }
        if (!p.getOrganizerId().equals(me.id())) {
            throw ApiException.forbidden("Action réservée à l'organisateur");
        }
        if (p.getStatus() != PotStatus.OPEN) {
            throw ApiException.conflict("POT_CLOSED", p.getStatus() == PotStatus.DRAFT ? "La cagnotte n'est pas encore publiée" : "La cagnotte est déjà close");
        }
        p.setStatus(PotStatus.CLOSED);
        p.setClosedAt(clock.instant());
        return toDto(me, pots.saveAndFlush(p));
    }

    @Transactional
    public void delete(AuthUser me, UUID id) {
        Pot p = pots.findById(id).orElseThrow(() -> ApiException.notFound("Cagnotte"));
        if (!visible(me, p, true)) {
            throw ApiException.notFound("Cagnotte");
        }
        if (!me.isAdmin()) {
            if (!p.getOrganizerId().equals(me.id())) {
                throw ApiException.forbidden("Action réservée à l'organisateur ou à un administrateur");
            }
            if (p.getStatus() != PotStatus.DRAFT && contributions.existsByPotId(id)) {
                throw ApiException.conflict("POT_HAS_CONTRIBUTIONS", "Des collègues ont déjà participé : la cagnotte ne peut plus être supprimée");
            }
        }
        pots.delete(p);
    }

    // ---------- règles de visibilité ----------

    private Pot refresh(Pot p) {
        if (p.getStatus() == PotStatus.OPEN && p.getClosesAt() != null && !clock.instant().isBefore(p.getClosesAt())) {
            p.setStatus(PotStatus.CLOSED);
            p.setClosedAt(p.getClosesAt());
            pots.save(p);
        }
        return p;
    }

    private boolean visible(AuthUser me, Pot p, boolean adminSeesAll) {
        if (p.getOrganizerId().equals(me.id())) {
            return true;
        }
        if (adminSeesAll && me.isAdmin()) {
            return true;
        }
        if (p.getStatus() == PotStatus.DRAFT) {
            return false;
        }
        if (p.getBeneficiaryId().equals(me.id())) {
            return p.getStatus() == PotStatus.CLOSED;
        }
        return p.getInviteeIds().contains(me.id());
    }

    private Pot visibleOr404(AuthUser me, UUID id) {
        Pot p = pots.findById(id).map(this::refresh).orElseThrow(() -> ApiException.notFound("Cagnotte"));
        if (!visible(me, p, true)) {
            throw ApiException.notFound("Cagnotte");
        }
        return p;
    }

    private Pot draft(AuthUser me, UUID id) {
        Pot p = pots.findById(id).filter(x -> x.getOrganizerId().equals(me.id())).orElseThrow(() -> ApiException.notFound("Cagnotte"));
        if (p.getStatus() != PotStatus.DRAFT) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "La cagnotte est déjà publiée");
        }
        return p;
    }

    private PotDto toDto(AuthUser me, Pot p) {
        List<Contribution> cs = contributions.findByPotIdOrderByCreatedAtAsc(p.getId());
        boolean organizer = p.getOrganizerId().equals(me.id());
        String role = organizer ? "ORGANIZER" : p.getBeneficiaryId().equals(me.id()) ? "BENEFICIARY"
                : p.getInviteeIds().contains(me.id()) ? "INVITEE" : "ADMIN";
        BigDecimal total = cs.stream().map(Contribution::getAmount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        Integer progress = p.getGoal() == null ? null
                : total.multiply(BigDecimal.valueOf(100)).divide(p.getGoal(), 0, RoundingMode.DOWN).intValue();
        Map<UUID, String> names = userLookup.namesOf(cs, Contribution::getUserId);
        List<ParticipantDto> participants = cs.stream()
                .map(c -> new ParticipantDto(c.getUserId(), names.get(c.getUserId()), organizer ? c.getAmount() : null, c.getMessage() != null)).toList();
        Contribution mine = cs.stream().filter(c -> c.getUserId().equals(me.id())).findFirst().orElse(null);
        return new PotDto(p.getId(), p.getTitle(), p.getOccasion(), p.getBeneficiaryId(), userLookup.name(p.getBeneficiaryId()),
                p.getOrganizerId(), userLookup.name(p.getOrganizerId()), p.getGoal(), p.getClosesAt(), p.getStatus(), role,
                organizer ? p.getInviteeIds().stream().sorted().toList() : List.of(), p.getInviteeIds().size(), total, progress, participants,
                mine == null ? null : mine.getAmount(), mine == null ? null : mine.getMessage());
    }
}
