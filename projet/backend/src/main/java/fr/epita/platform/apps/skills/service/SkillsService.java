package fr.epita.platform.apps.skills.service;

import fr.epita.platform.apps.skills.dto.SkillsDtos.AskRequest;
import fr.epita.platform.apps.skills.dto.SkillsDtos.OfferDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.PairDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.ProfileDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.ProfileRequest;
import fr.epita.platform.apps.skills.dto.SkillsDtos.RequestDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.SessionDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.SessionRequest;
import fr.epita.platform.apps.skills.dto.SkillsDtos.SkillDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.StatsDto;
import fr.epita.platform.apps.skills.dto.SkillsDtos.SuggestionDto;
import fr.epita.platform.apps.skills.entity.CatalogSkill;
import fr.epita.platform.apps.skills.entity.MentoringRequest;
import fr.epita.platform.apps.skills.entity.MentoringSession;
import fr.epita.platform.apps.skills.entity.Pair;
import fr.epita.platform.apps.skills.entity.SkillProfile;
import fr.epita.platform.apps.skills.repository.CatalogSkillRepository;
import fr.epita.platform.apps.skills.repository.MentoringRequestRepository;
import fr.epita.platform.apps.skills.repository.MentoringSessionRepository;
import fr.epita.platform.apps.skills.repository.PairRepository;
import fr.epita.platform.apps.skills.repository.SkillProfileRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SkillsService {

    static final int MENTOR_MIN_LEVEL = 3;

    private final CatalogSkillRepository catalog;
    private final SkillProfileRepository profiles;
    private final MentoringRequestRepository requests;
    private final PairRepository pairs;
    private final MentoringSessionRepository sessions;
    private final UserLookup userLookup;
    private final Clock clock;

    // ---------- référentiel ----------

    @Transactional(readOnly = true)
    public List<SkillDto> catalog() {
        return catalog.findAllByOrderByNameAsc().stream().map(s -> new SkillDto(s.getId(), s.getName())).toList();
    }

    @Transactional
    public SkillDto addSkill(AuthUser me, String name) {
        me.requireAdmin();
        if (catalog.existsByNameIgnoreCase(name.trim())) {
            throw ApiException.conflict("SKILL_EXISTS", "Cette compétence existe déjà");
        }
        CatalogSkill s = new CatalogSkill();
        s.setName(name.trim());
        catalog.saveAndFlush(s);
        return new SkillDto(s.getId(), s.getName());
    }

    // ---------- profil ----------

    @Transactional
    public ProfileDto profile(AuthUser me) {
        return toDto(profileOf(me.id()));
    }

    @Transactional
    public ProfileDto updateProfile(AuthUser me, ProfileRequest req) {
        Set<UUID> known = catalog.findAll().stream().map(CatalogSkill::getId).collect(Collectors.toSet());
        var errors = new ValidationException.Collector();
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < req.offers().size(); i++) {
            UUID s = req.offers().get(i).skillId();
            errors.check(known.contains(s), "offers[" + i + "].skillId", "doit appartenir au référentiel");
            errors.check(seen.add(s), "offers[" + i + "].skillId", "compétence déjà proposée");
        }
        for (int i = 0; i < req.wants().size(); i++) {
            errors.check(known.contains(req.wants().get(i)), "wants[" + i + "]", "doit appartenir au référentiel");
        }
        long active = pairs.countByMentorIdAndStatus(me.id(), "ACTIVE");
        errors.check(req.capacity() >= active, "capacity", "ne peut pas être inférieure au nombre d'apprenants actifs (" + active + ")");
        errors.throwIfAny();
        SkillProfile p = profileOf(me.id());
        p.getOffers().clear();
        req.offers().forEach(o -> p.getOffers().put(o.skillId(), o.level()));
        p.getWants().clear();
        p.getWants().addAll(req.wants());
        p.setCapacity(req.capacity());
        return toDto(profiles.saveAndFlush(p));
    }

    /** Mentors de niveau ≥ 3, triés par niveau puis par places restantes ; jamais soi-même, jamais un mentor complet. */
    @Transactional(readOnly = true)
    public List<SuggestionDto> suggestions(AuthUser me, UUID skillId) {
        return profiles.findAll().stream()
                .filter(p -> !p.getUserId().equals(me.id()))
                .filter(p -> p.getOffers().getOrDefault(skillId, 0) >= MENTOR_MIN_LEVEL)
                .map(p -> new SuggestionDto(p.getUserId(), userLookup.name(p.getUserId()), p.getOffers().get(skillId),
                        (int) Math.max(0, p.getCapacity() - pairs.countByMentorIdAndStatus(p.getUserId(), "ACTIVE"))))
                .filter(s -> s.remainingPlaces() > 0)
                .sorted(Comparator.comparingInt(SuggestionDto::level).reversed()
                        .thenComparing(Comparator.comparingInt(SuggestionDto::remainingPlaces).reversed()))
                .toList();
    }

    // ---------- demandes ----------

    @Transactional
    public RequestDto ask(AuthUser me, AskRequest req) {
        if (req.mentorId().equals(me.id())) {
            throw ApiException.conflict("SELF_MENTORING", "On ne peut pas être son propre mentor");
        }
        CatalogSkill skill = catalog.findById(req.skillId()).orElseThrow(() -> ApiException.notFound("Compétence"));
        userLookup.require(req.mentorId());
        int level = profiles.findById(req.mentorId()).map(p -> p.getOffers().getOrDefault(skill.getId(), 0)).orElse(0);
        if (level < MENTOR_MIN_LEVEL) {
            throw ValidationException.field("mentorId", "cette personne ne propose pas cette compétence au niveau 3 ou plus");
        }
        Instant now = clock.instant();
        boolean pending = requests.findByLearnerIdOrderByCreatedAtDesc(me.id()).stream()
                .anyMatch(r -> r.getSkillId().equals(skill.getId()) && "PENDING".equals(r.effectiveStatus(now)));
        boolean paired = !pairs.findByLearnerIdAndSkillIdAndStatus(me.id(), skill.getId(), "ACTIVE").isEmpty();
        if (pending || paired) {
            throw ApiException.conflict("ALREADY_PAIRED", "Vous avez déjà une demande ou un binôme actif sur cette compétence");
        }
        MentoringRequest r = new MentoringRequest();
        r.setLearnerId(me.id());
        r.setMentorId(req.mentorId());
        r.setSkillId(skill.getId());
        r.setMessage(req.message() == null || req.message().isBlank() ? null : req.message().trim());
        return toDto(requests.saveAndFlush(r));
    }

    @Transactional(readOnly = true)
    public List<RequestDto> myRequests(AuthUser me, String box) {
        List<MentoringRequest> list = "received".equals(box) ? requests.findByMentorIdOrderByCreatedAtDesc(me.id())
                : "sent".equals(box) || box == null ? requests.findByLearnerIdOrderByCreatedAtDesc(me.id())
                : null;
        if (list == null) {
            throw ValidationException.field("box", "Valeurs possibles : sent, received");
        }
        return list.stream().map(this::toDto).toList();
    }

    /** Acceptation par le mentor, sous verrou de son profil : la capacité n'est jamais dépassée. */
    @Transactional
    public PairDto accept(AuthUser me, UUID id) {
        MentoringRequest r = pendingFor(me, id);
        SkillProfile mentor = profiles.lock(me.id()).orElseGet(() -> profileOf(me.id()));
        if (pairs.countByMentorIdAndStatus(me.id(), "ACTIVE") >= mentor.getCapacity()) {
            throw ApiException.conflict("MENTOR_FULL", "Vous avez atteint votre nombre maximum d'apprenants");
        }
        r.setStatus("ACCEPTED");
        r.setDecidedAt(clock.instant());
        Pair p = new Pair();
        p.setMentorId(r.getMentorId());
        p.setLearnerId(r.getLearnerId());
        p.setSkillId(r.getSkillId());
        return toDto(pairs.saveAndFlush(p), me);
    }

    @Transactional
    public RequestDto decline(AuthUser me, UUID id) {
        MentoringRequest r = pendingFor(me, id);
        r.setStatus("DECLINED");
        r.setDecidedAt(clock.instant());
        return toDto(r);
    }

    // ---------- binômes ----------

    @Transactional(readOnly = true)
    public List<PairDto> myPairs(AuthUser me) {
        return pairs.findByMentorIdOrLearnerIdOrderByCreatedAtDesc(me.id(), me.id()).stream().map(p -> toDto(p, me)).toList();
    }

    @Transactional
    public PairDto addSession(AuthUser me, UUID pairId, SessionRequest req) {
        Pair p = memberPair(me, pairId);
        if (!p.isActive()) {
            throw ApiException.conflict("PAIR_ENDED", "Ce binôme est terminé");
        }
        MentoringSession s = new MentoringSession();
        s.setPairId(pairId);
        s.setAuthorId(me.id());
        s.setDate(req.date());
        s.setDurationMinutes(req.durationMinutes());
        s.setNotes(req.notes() == null || req.notes().isBlank() ? null : req.notes().trim());
        sessions.saveAndFlush(s);
        return toDto(p, me);
    }

    @Transactional
    public PairDto end(AuthUser me, UUID pairId) {
        Pair p = memberPair(me, pairId);
        if (!p.isActive()) {
            throw ApiException.conflict("PAIR_ENDED", "Ce binôme est déjà terminé");
        }
        p.setStatus("ENDED");
        p.setEndedAt(clock.instant());
        return toDto(p, me);
    }

    @Transactional(readOnly = true)
    public StatsDto stats(AuthUser me) {
        me.requireAdmin();
        Map<UUID, String> names = catalog.findAll().stream().collect(Collectors.toMap(CatalogSkill::getId, CatalogSkill::getName));
        Map<String, Long> wanted = new HashMap<>();
        profiles.findAll().forEach(p -> p.getWants().forEach(w -> wanted.merge(names.getOrDefault(w, "?"), 1L, Long::sum)));
        List<MentoringRequest> all = requests.findAll();
        all.forEach(r -> wanted.merge(names.getOrDefault(r.getSkillId(), "?"), 1L, Long::sum));
        Map<String, Long> mentors = all.stream().collect(Collectors.groupingBy(r -> userLookup.name(r.getMentorId()), Collectors.counting()));
        long active = pairs.findAll().stream().filter(Pair::isActive).count();
        return new StatsDto(top(wanted), top(mentors), active);
    }

    // ---------- outils ----------

    private static Map<String, Long> top(Map<String, Long> m) {
        return m.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(10)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    private MentoringRequest pendingFor(AuthUser me, UUID id) {
        MentoringRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("Demande"));
        if (!r.getMentorId().equals(me.id())) {
            throw ApiException.forbidden("Seul le mentor sollicité répond à la demande");
        }
        String status = r.effectiveStatus(clock.instant());
        if (!"PENDING".equals(status)) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Cette demande est " + status);
        }
        return r;
    }

    private Pair memberPair(AuthUser me, UUID id) {
        Pair p = pairs.findById(id).orElseThrow(() -> ApiException.notFound("Binôme"));
        if (!p.getMentorId().equals(me.id()) && !p.getLearnerId().equals(me.id())) {
            throw ApiException.forbidden("Réservé aux membres du binôme");
        }
        return p;
    }

    private SkillProfile profileOf(UUID userId) {
        return profiles.findById(userId).orElseGet(() -> profiles.saveAndFlush(new SkillProfile(userId)));
    }

    private Map<UUID, String> skillNames() {
        return catalog.findAll().stream().collect(Collectors.toMap(CatalogSkill::getId, CatalogSkill::getName));
    }

    private ProfileDto toDto(SkillProfile p) {
        Map<UUID, CatalogSkill> byId = catalog.findAll().stream().collect(Collectors.toMap(CatalogSkill::getId, Function.identity()));
        List<OfferDto> offers = p.getOffers().entrySet().stream().filter(e -> byId.containsKey(e.getKey()))
                .map(e -> new OfferDto(e.getKey(), byId.get(e.getKey()).getName(), e.getValue()))
                .sorted(Comparator.comparing(OfferDto::name)).toList();
        List<SkillDto> wants = p.getWants().stream().filter(byId::containsKey)
                .map(id -> new SkillDto(id, byId.get(id).getName())).sorted(Comparator.comparing(SkillDto::name)).toList();
        return new ProfileDto(p.getUserId(), offers, wants, p.getCapacity(), pairs.countByMentorIdAndStatus(p.getUserId(), "ACTIVE"));
    }

    private RequestDto toDto(MentoringRequest r) {
        return new RequestDto(r.getId(), r.getLearnerId(), userLookup.name(r.getLearnerId()), r.getMentorId(),
                userLookup.name(r.getMentorId()), r.getSkillId(), skillNames().get(r.getSkillId()), r.getMessage(),
                r.effectiveStatus(clock.instant()), r.getCreatedAt(), r.getDecidedAt());
    }

    private PairDto toDto(Pair p, AuthUser me) {
        List<SessionDto> list = sessions.findByPairIdOrderByDateDesc(p.getId()).stream()
                .map(s -> new SessionDto(s.getId(), s.getDate(), s.getDurationMinutes(), s.getNotes(), userLookup.name(s.getAuthorId())))
                .toList();
        return new PairDto(p.getId(), p.getMentorId(), userLookup.name(p.getMentorId()), p.getLearnerId(),
                userLookup.name(p.getLearnerId()), p.getSkillId(), skillNames().get(p.getSkillId()), p.getStatus(),
                p.getMentorId().equals(me.id()) ? "MENTOR" : "LEARNER", list.stream().mapToInt(SessionDto::durationMinutes).sum(),
                list, p.getCreatedAt());
    }
}
