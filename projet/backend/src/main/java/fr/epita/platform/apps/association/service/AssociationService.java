package fr.epita.platform.apps.association.service;

import fr.epita.platform.apps.association.dto.AssociationDtos.*;
import fr.epita.platform.apps.association.entity.*;
import fr.epita.platform.apps.association.repository.*;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import fr.epita.platform.user.entity.User;
import fr.epita.platform.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Saison du 1er septembre au 31 août ; « à jour » = cotisation de la saison en cours. Activités avec liste d'attente
 * (règles du §5.9) ; AG à vote secret : décompte par résolution et émargement séparé, sans lien entre les deux.
 */
@Service
@RequiredArgsConstructor
public class AssociationService {

    public static final String ROLES = "association_roles";
    public static final String BOARD = "BOARD";
    private static final ZoneId ZONE = ZoneId.of("Europe/Paris");

    private final AssociationFeeRepository fees;
    private final AssociationActivityRepository activities;
    private final AssociationRegistrationRepository registrations;
    private final AssemblyRepository assemblies;
    private final ResolutionRepository resolutions;
    private final BallotRepository ballots;
    private final UserRepository users;
    private final AppRoles roles;
    private final UserLookup userLookup;
    private final Clock clock;

    /** Saison contenant la date : septembre 2026 → « 2026-2027 », mars 2027 → « 2026-2027 ». */
    public static String seasonOf(LocalDate date) {
        int start = date.getMonthValue() >= 9 ? date.getYear() : date.getYear() - 1;
        return start + "-" + (start + 1);
    }

    String currentSeason() {
        return seasonOf(LocalDate.now(clock.withZone(ZONE)));
    }

    boolean upToDate(UUID userId) {
        return fees.existsByUserIdAndSeason(userId, currentSeason());
    }

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(BOARD));
    }

    // ---------- adhérents et cotisations ----------

    @Transactional(readOnly = true)
    public MyMembershipDto me(AuthUser me) {
        return new MyMembershipDto(currentSeason(), upToDate(me.id()), roles.has(ROLES, me, BOARD),
                fees.findByUserIdOrderBySeasonDesc(me.id()).stream().map(this::toDto).toList());
    }

    @Transactional(readOnly = true)
    public List<MemberDto> members(AuthUser me, Boolean upToDate, String search) {
        roles.require(ROLES, me, BOARD);
        String term = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream()
                .filter(u -> term == null || u.getDisplayName().toLowerCase(Locale.ROOT).contains(term) || u.getEmail().contains(term))
                .map(this::toMember).filter(m -> upToDate == null || m.upToDate() == upToDate).toList();
    }

    @Transactional
    public FeeDto recordFee(AuthUser me, UUID userId, FeeRequest req) {
        roles.require(ROLES, me, BOARD);
        userLookup.require(userId);
        String season = req.season() == null ? currentSeason() : req.season();
        int start = Integer.parseInt(season.substring(0, 4));
        if (Integer.parseInt(season.substring(5)) != start + 1) {
            throw ValidationException.field("season", "deux années consécutives (ex. 2026-2027)");
        }
        if (req.paidAt().isAfter(LocalDate.now(clock.withZone(ZONE)))) {
            throw ValidationException.field("paidAt", "ne doit pas être dans le futur");
        }
        if (fees.existsByUserIdAndSeason(userId, season)) {
            throw ApiException.conflict("ALREADY_PAID", "Cotisation déjà enregistrée pour la saison " + season);
        }
        AssociationFee f = new AssociationFee();
        f.setUserId(userId);
        f.setSeason(season);
        f.setCategory(req.category());
        f.setAmount(req.amount());
        f.setPaidAt(req.paidAt());
        f.setRecordedBy(me.id());
        try {
            return toDto(fees.saveAndFlush(f));
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("ALREADY_PAID", "Cotisation déjà enregistrée pour la saison " + season);
        }
    }

    /** Adhérents à jour la saison précédente mais pas encore pour la saison en cours. */
    @Transactional(readOnly = true)
    public List<MemberDto> reminders(AuthUser me) {
        roles.require(ROLES, me, BOARD);
        String current = currentSeason();
        int start = Integer.parseInt(current.substring(0, 4));
        String previous = (start - 1) + "-" + start;
        Set<UUID> paidNow = fees.findBySeason(current).stream().map(AssociationFee::getUserId).collect(Collectors.toSet());
        Set<UUID> paidBefore = fees.findBySeason(previous).stream().map(AssociationFee::getUserId).collect(Collectors.toSet());
        return users.findByEnabledTrueOrderByDisplayNameAsc().stream()
                .filter(u -> paidBefore.contains(u.getId()) && !paidNow.contains(u.getId())).map(this::toMember).toList();
    }

    // ---------- activités ----------

    @Transactional(readOnly = true)
    public List<ActivityDto> activities(AuthUser me) {
        return activities.findAllByOrderByDateAsc().stream().map(a -> toDto(me, a)).toList();
    }

    @Transactional
    public ActivityDto createActivity(AuthUser me, ActivityRequest req) {
        roles.require(ROLES, me, BOARD);
        if (!req.date().isAfter(clock.instant())) {
            throw ValidationException.field("date", "doit être dans le futur");
        }
        AssociationActivity a = new AssociationActivity();
        a.setTitle(req.title().trim());
        a.setDate(req.date());
        a.setCapacity(req.capacity());
        a.setMembersOnly(req.membersOnly());
        return toDto(me, activities.saveAndFlush(a));
    }

    @Transactional
    public ActivityDto register(AuthUser me, UUID id) {
        AssociationActivity a = openActivity(id);
        if (a.isMembersOnly() && !upToDate(me.id())) {
            throw ApiException.conflict("MEMBERSHIP_EXPIRED", "Activité réservée aux adhérents à jour de cotisation (saison " + currentSeason() + ")");
        }
        if (registrations.findByActivityIdAndUserId(id, me.id()).isPresent()) {
            throw ApiException.conflict("ALREADY_REGISTERED", "Vous êtes déjà inscrit");
        }
        long confirmed = registrations.countByActivityIdAndStatus(id, AssociationRegistrationStatus.CONFIRMED);
        AssociationRegistration r = new AssociationRegistration();
        r.setActivityId(id);
        r.setUserId(me.id());
        r.setStatus(confirmed < a.getCapacity() ? AssociationRegistrationStatus.CONFIRMED : AssociationRegistrationStatus.WAITLISTED);
        registrations.saveAndFlush(r);
        return toDto(me, a);
    }

    /** Désinscription : le premier en attente prend la place libérée, dans la même transaction. */
    @Transactional
    public ActivityDto unregister(AuthUser me, UUID id) {
        AssociationActivity a = openActivity(id);
        AssociationRegistration r = registrations.findByActivityIdAndUserId(id, me.id()).orElseThrow(() -> ApiException.notFound("Inscription"));
        boolean wasConfirmed = r.getStatus() == AssociationRegistrationStatus.CONFIRMED;
        registrations.delete(r);
        registrations.flush();
        if (wasConfirmed) {
            registrations.findByActivityIdOrderByCreatedAtAscIdAsc(id).stream().filter(x -> x.getStatus() == AssociationRegistrationStatus.WAITLISTED)
                    .findFirst().ifPresent(next -> {
                        next.setStatus(AssociationRegistrationStatus.CONFIRMED);
                        registrations.saveAndFlush(next);
                    });
        }
        return toDto(me, a);
    }

    private AssociationActivity openActivity(UUID id) {
        AssociationActivity a = activities.lock(id).orElseThrow(() -> ApiException.notFound("Activité"));
        if (!a.getDate().isAfter(clock.instant())) {
            throw ApiException.conflict("REGISTRATION_CLOSED", "L'activité a déjà eu lieu");
        }
        return a;
    }

    // ---------- assemblée générale ----------

    @Transactional(readOnly = true)
    public List<AssemblyDto> assemblies(AuthUser me) {
        return assemblies.findAllByOrderByDateDesc().stream().map(a -> toDto(me, a)).toList();
    }

    @Transactional
    public AssemblyDto convene(AuthUser me, AssemblyRequest req) {
        roles.require(ROLES, me, BOARD);
        Assembly a = new Assembly();
        a.setTitle(req.title().trim());
        a.setDate(req.date());
        a.setQuorumPercent(req.quorumPercent());
        a.setStatus(AssemblyStatus.CONVENED);
        assemblies.saveAndFlush(a);
        for (int i = 0; i < req.resolutions().size(); i++) {
            Resolution r = new Resolution();
            r.setId(UUID.randomUUID());
            r.setAssemblyId(a.getId());
            r.setPosition(i + 1);
            r.setTitle(req.resolutions().get(i).title().trim());
            r.setText(req.resolutions().get(i).text().trim());
            resolutions.save(r);
        }
        resolutions.flush();
        return toDto(me, a);
    }

    @Transactional(readOnly = true)
    public AssemblyDto assembly(AuthUser me, UUID id) {
        return toDto(me, assemblies.findById(id).orElseThrow(() -> ApiException.notFound("Assemblée")));
    }

    @Transactional
    public AssemblyDto open(AuthUser me, UUID id) {
        roles.require(ROLES, me, BOARD);
        Assembly a = assemblies.lock(id).orElseThrow(() -> ApiException.notFound("Assemblée"));
        if (a.getStatus() != AssemblyStatus.CONVENED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Le vote a déjà été ouvert");
        }
        a.setStatus(AssemblyStatus.OPEN);
        return toDto(me, assemblies.saveAndFlush(a));
    }

    /** Vote secret : émargement (qui a voté) et décompte (combien) sont stockés séparément. */
    @Transactional
    public AssemblyDto vote(AuthUser me, UUID id, List<VoteInput> votes) {
        Assembly a = assemblies.lock(id).orElseThrow(() -> ApiException.notFound("Assemblée"));
        if (a.getStatus() != AssemblyStatus.OPEN) {
            throw ApiException.conflict("VOTE_NOT_OPEN", "Le vote n'est pas ouvert");
        }
        if (!upToDate(me.id())) {
            throw ApiException.conflict("MEMBERSHIP_EXPIRED", "Seuls les adhérents à jour de cotisation votent");
        }
        Set<UUID> ids = resolutions.findByAssemblyIdOrderByPositionAsc(id).stream().map(Resolution::getId).collect(Collectors.toSet());
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < votes.size(); i++) {
            UUID r = votes.get(i).resolutionId();
            if (!ids.contains(r) || !seen.add(r)) {
                throw ValidationException.field("votes[" + i + "].resolutionId", "résolution étrangère à l'AG ou en double");
            }
            if (ballots.existsById(new Ballot.Key(r, me.id()))) {
                throw ApiException.conflict("ALREADY_VOTED", "Vous avez déjà voté sur cette résolution");
            }
        }
        for (VoteInput v : votes) {
            ballots.save(new Ballot(new Ballot.Key(v.resolutionId(), me.id())));
            switch (v.choice()) {
                case FOR -> resolutions.addFor(v.resolutionId());
                case AGAINST -> resolutions.addAgainst(v.resolutionId());
                case ABSTAIN -> resolutions.addAbstain(v.resolutionId());
            }
        }
        ballots.flush();
        return toDto(me, a);
    }

    /** Clôture : nombre d'adhérents à jour et de votants figés, résultats publiés. */
    @Transactional
    public AssemblyDto close(AuthUser me, UUID id) {
        roles.require(ROLES, me, BOARD);
        Assembly a = assemblies.lock(id).orElseThrow(() -> ApiException.notFound("Assemblée"));
        if (a.getStatus() != AssemblyStatus.OPEN) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Le vote n'est pas ouvert");
        }
        a.setEligible(fees.findBySeason(currentSeason()).size());
        a.setVoters((int) voters(a));
        a.setStatus(AssemblyStatus.CLOSED);
        return toDto(me, assemblies.saveAndFlush(a));
    }

    static boolean quorumReached(int voters, int eligible, int quorumPercent) {
        return eligible > 0 && voters * 100L >= (long) quorumPercent * eligible;
    }

    private long voters(Assembly a) {
        List<UUID> ids = resolutions.findByAssemblyIdOrderByPositionAsc(a.getId()).stream().map(Resolution::getId).toList();
        return ids.isEmpty() ? 0 : ballots.countVoters(ids);
    }

    // ---------- conversions ----------

    private FeeDto toDto(AssociationFee f) {
        return new FeeDto(f.getId(), f.getSeason(), f.getCategory(), f.getAmount(), f.getPaidAt(), f.getRecordedBy() == null ? null : userLookup.name(f.getRecordedBy()));
    }

    private MemberDto toMember(User u) {
        Optional<AssociationFee> last = fees.findByUserIdOrderBySeasonDesc(u.getId()).stream().findFirst();
        return new MemberDto(u.getId(), u.getDisplayName(), u.getEmail(), last.map(f -> f.getSeason().equals(currentSeason())).orElse(false),
                last.map(AssociationFee::getSeason).orElse(null), last.map(AssociationFee::getCategory).orElse(null));
    }

    private ActivityDto toDto(AuthUser me, AssociationActivity a) {
        List<AssociationRegistration> list = registrations.findByActivityIdOrderByCreatedAtAscIdAsc(a.getId());
        long confirmed = list.stream().filter(r -> r.getStatus() == AssociationRegistrationStatus.CONFIRMED).count();
        List<AssociationRegistration> waiting = list.stream().filter(r -> r.getStatus() == AssociationRegistrationStatus.WAITLISTED).toList();
        Optional<AssociationRegistration> mine = list.stream().filter(r -> r.getUserId().equals(me.id())).findFirst();
        Integer position = mine.filter(r -> r.getStatus() == AssociationRegistrationStatus.WAITLISTED).map(r -> waiting.indexOf(r) + 1).orElse(null);
        return new ActivityDto(a.getId(), a.getTitle(), a.getDate(), a.getCapacity(), a.isMembersOnly(), confirmed, waiting.size(),
                mine.map(AssociationRegistration::getStatus).orElse(null), position, a.getDate().isAfter(clock.instant()));
    }

    private AssemblyDto toDto(AuthUser me, Assembly a) {
        List<Resolution> rs = resolutions.findByAssemblyIdOrderByPositionAsc(a.getId());
        Set<UUID> mine = ballots.findByIdResolutionIdIn(rs.stream().map(Resolution::getId).toList()).stream()
                .filter(b -> b.getId().getUserId().equals(me.id())).map(b -> b.getId().getResolutionId()).collect(Collectors.toSet());
        boolean closed = a.getStatus() == AssemblyStatus.CLOSED;
        Boolean quorum = closed ? quorumReached(a.getVoters(), a.getEligible(), a.getQuorumPercent()) : null;
        List<ResolutionDto> list = rs.stream().map(r -> new ResolutionDto(r.getId(), r.getTitle(), r.getText(), mine.contains(r.getId()),
                closed ? r.getForCount() : null, closed ? r.getAgainstCount() : null, closed ? r.getAbstainCount() : null,
                closed ? quorum && r.getForCount() > r.getAgainstCount() : null)).toList();
        boolean canVote = a.getStatus() == AssemblyStatus.OPEN && upToDate(me.id()) && mine.size() < rs.size();
        return new AssemblyDto(a.getId(), a.getTitle(), a.getDate(), a.getQuorumPercent(), a.getStatus(), canVote,
                closed ? a.getVoters() : voters(a), a.getEligible(), quorum, list);
    }
}
