package fr.epita.platform.apps.referral.service;

import fr.epita.platform.apps.referral.dto.ReferralDtos.*;
import fr.epita.platform.apps.referral.entity.Referral;
import fr.epita.platform.apps.referral.entity.ReferralJob;
import fr.epita.platform.apps.referral.entity.ReferralStatus;
import fr.epita.platform.apps.referral.repository.ReferralJobRepository;
import fr.epita.platform.apps.referral.repository.ReferralRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Expiration « paresseuse » : avant chaque lecture, les recommandations SUBMITTED / IN_REVIEW
 * sans évolution depuis 60 jours passent EXPIRED.
 */
@Service
@RequiredArgsConstructor
public class ReferralService {

    static final Duration EXPIRY = Duration.ofDays(60);
    private static final EnumSet<ReferralStatus> EXPIRABLE = EnumSet.of(ReferralStatus.SUBMITTED, ReferralStatus.IN_REVIEW);
    private static final EnumSet<ReferralStatus> REJECTABLE = EnumSet.of(ReferralStatus.SUBMITTED, ReferralStatus.IN_REVIEW, ReferralStatus.HIRED);

    private final ReferralJobRepository jobs;
    private final ReferralRepository referrals;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- postes ----------

    @Transactional(readOnly = true)
    public List<JobDto> jobs(AuthUser me) {
        return (me.isAdmin() ? jobs.findAllByOrderByCreatedAtDesc() : jobs.findByOpenTrueOrderByCreatedAtDesc()).stream()
                .map(j -> toDto(j, me.isAdmin())).toList();
    }

    @Transactional
    public JobDto createJob(AuthUser me, JobRequest req) {
        me.requireAdmin();
        ReferralJob j = new ReferralJob();
        apply(j, req);
        return toDto(jobs.saveAndFlush(j), true);
    }

    @Transactional
    public JobDto updateJob(AuthUser me, UUID id, JobRequest req) {
        me.requireAdmin();
        ReferralJob j = job(id);
        apply(j, req);
        return toDto(jobs.saveAndFlush(j), true);
    }

    private void apply(ReferralJob j, JobRequest req) {
        j.setTitle(req.title().trim());
        j.setTeam(req.team().trim());
        j.setDescription(req.description().trim());
        j.setBonusAmount(req.bonusAmount());
        j.setOpen(req.open());
    }

    // ---------- recommandations ----------

    @Transactional
    public MyReferralDto refer(AuthUser me, ReferralRequest req) {
        ReferralJob j = job(req.jobId());
        if (!j.isOpen()) {
            throw ApiException.conflict("JOB_CLOSED", "Ce poste n'est plus ouvert à la cooptation");
        }
        String email = req.candidateEmail().trim().toLowerCase();
        if (email.equalsIgnoreCase(me.email())) {
            throw ApiException.conflict("SELF_REFERRAL", "Vous ne pouvez pas vous recommander vous-même");
        }
        if (referrals.existsByJobIdAndCandidateEmail(j.getId(), email)) {
            throw ApiException.conflict("ALREADY_REFERRED", "Cette personne a déjà été recommandée pour ce poste");
        }
        Referral r = new Referral();
        r.setJobId(j.getId());
        r.setReferrerId(me.id());
        r.setCandidateName(req.candidateName().trim());
        r.setCandidateEmail(email);
        r.setLinkedinUrl(req.linkedinUrl() == null || req.linkedinUrl().isBlank() ? null : req.linkedinUrl().trim());
        r.setMessage(req.message().trim());
        r.setStatus(ReferralStatus.SUBMITTED);
        r.setStatusChangedAt(clock.instant());
        try {
            referrals.saveAndFlush(r);
        } catch (DataIntegrityViolationException e) {
            // deux recommandations simultanées du même candidat : la contrainte d'unicité retient la première
            throw ApiException.conflict("ALREADY_REFERRED", "Cette personne a déjà été recommandée pour ce poste");
        }
        return toMine(r);
    }

    @Transactional
    public List<MyReferralDto> mine(AuthUser me) {
        expire();
        return referrals.findByReferrerIdOrderByCreatedAtDesc(me.id()).stream().map(this::toMine).toList();
    }

    @Transactional
    public List<ReferralDto> adminList(AuthUser me, ReferralStatus status, UUID jobId) {
        me.requireAdmin();
        expire();
        Predicate<Referral> filter = r -> (status == null || r.getStatus() == status) && (jobId == null || r.getJobId().equals(jobId));
        return referrals.findAllByOrderByCreatedAtDesc().stream().filter(filter).map(this::toAdmin).toList();
    }

    /** Une étape à la fois dans l'ordre, ou REJECTED avant la validation de la période d'essai. */
    @Transactional
    public ReferralDto changeStatus(AuthUser me, UUID id, StatusRequest req) {
        me.requireAdmin();
        expire();
        Referral r = referrals.lock(id).orElseThrow(() -> ApiException.notFound("Recommandation"));
        ReferralStatus from = r.getStatus();
        ReferralStatus to = req.status();
        boolean next = ReferralStatus.FLOW.indexOf(from) >= 0 && ReferralStatus.FLOW.indexOf(to) == ReferralStatus.FLOW.indexOf(from) + 1;
        boolean reject = to == ReferralStatus.REJECTED && REJECTABLE.contains(from);
        if (to == ReferralStatus.BONUS_PAID) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Le versement de la prime se fait par /bonus-paid");
        }
        if (!next && !reject) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Passage de " + from + " à " + to + " interdit");
        }
        r.setStatus(to);
        r.setStatusChangedAt(clock.instant());
        if (req.internalComment() != null && !req.internalComment().isBlank()) {
            r.setInternalComment(req.internalComment().trim());
        }
        return toAdmin(referrals.saveAndFlush(r));
    }

    /** Primes : `due` = période d'essai validée et coopteur encore actif ; `paid` = versées ; sans filtre : les deux. */
    @Transactional
    public List<ReferralDto> bonuses(AuthUser me, String status) {
        me.requireAdmin();
        expire();
        List<Referral> list = switch (status == null ? "due" : status) {
            case "due" -> referrals.findByStatusOrderByStatusChangedAtAsc(ReferralStatus.TRIAL_VALIDATED).stream()
                    .filter(r -> referrerActive(r.getReferrerId())).toList();
            case "paid" -> referrals.findByStatusOrderByStatusChangedAtAsc(ReferralStatus.BONUS_PAID);
            default -> throw ValidationException.field("status", "doit valoir due ou paid");
        };
        return list.stream().map(this::toAdmin).toList();
    }

    @Transactional
    public ReferralDto bonusPaid(AuthUser me, UUID id) {
        me.requireAdmin();
        Referral r = referrals.lock(id).orElseThrow(() -> ApiException.notFound("Recommandation"));
        if (r.getStatus() == ReferralStatus.BONUS_PAID) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Prime déjà versée");
        }
        if (r.getStatus() != ReferralStatus.TRIAL_VALIDATED) {
            throw ApiException.conflict("TRIAL_NOT_VALIDATED", "La prime n'est due qu'après la validation de la période d'essai");
        }
        if (!referrerActive(r.getReferrerId())) {
            throw ApiException.conflict("REFERRER_INACTIVE", "Le coopteur ne fait plus partie de l'entreprise : prime non due");
        }
        r.setStatus(ReferralStatus.BONUS_PAID);
        r.setStatusChangedAt(clock.instant());
        r.setBonusPaidAt(clock.instant());
        return toAdmin(referrals.saveAndFlush(r));
    }

    // ---------- outils ----------

    private void expire() {
        referrals.expire(EXPIRABLE, clock.instant().minus(EXPIRY), clock.instant());
    }

    private boolean referrerActive(UUID userId) {
        try {
            return userLookup.require(userId).isEnabled();
        } catch (ApiException e) {
            return false;
        }
    }

    private ReferralJob job(UUID id) {
        return jobs.findById(id).orElseThrow(() -> ApiException.notFound("Poste"));
    }

    private JobDto toDto(ReferralJob j, boolean withCount) {
        return new JobDto(j.getId(), j.getTitle(), j.getTeam(), j.getDescription(), j.getBonusAmount(), j.isOpen(),
                withCount ? referrals.countByJobId(j.getId()) : 0);
    }

    private MyReferralDto toMine(Referral r) {
        ReferralJob j = job(r.getJobId());
        return new MyReferralDto(r.getId(), j.getId(), j.getTitle(), j.getBonusAmount(), r.getCandidateName(), r.getCandidateEmail(),
                r.getStatus(), r.getStatusChangedAt(), r.getCreatedAt(), r.getBonusPaidAt());
    }

    private ReferralDto toAdmin(Referral r) {
        ReferralJob j = job(r.getJobId());
        User referrer = userLookup.require(r.getReferrerId());
        return new ReferralDto(r.getId(), j.getId(), j.getTitle(), j.getBonusAmount(), referrer.getId(), referrer.getDisplayName(),
                referrer.isEnabled(), r.getCandidateName(), r.getCandidateEmail(), r.getLinkedinUrl(), r.getMessage(), r.getStatus(),
                r.getInternalComment(), r.getStatusChangedAt(), r.getCreatedAt(), r.getBonusPaidAt());
    }
}
