package fr.epita.platform.apps.jargon.service;

import fr.epita.platform.apps.jargon.dto.JargonDtos.*;
import fr.epita.platform.apps.jargon.entity.ContributionStatus;
import fr.epita.platform.apps.jargon.entity.JargonDefinition;
import fr.epita.platform.apps.jargon.entity.JargonTerm;
import fr.epita.platform.apps.jargon.entity.JargonVote;
import fr.epita.platform.apps.jargon.repository.JargonDefinitionRepository;
import fr.epita.platform.apps.jargon.repository.JargonTermRepository;
import fr.epita.platform.apps.jargon.repository.JargonVoteRepository;
import fr.epita.platform.apps.jargon.repository.MissingSearchRepository;
import fr.epita.platform.common.ApiException;
import fr.epita.platform.common.AuthUser;
import fr.epita.platform.common.PageResponse;
import fr.epita.platform.common.UserLookup;
import fr.epita.platform.common.ValidationException;
import fr.epita.platform.common.relation.AppRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Stream;

/**
 * Glossaire modéré : termes et définitions restent PENDING jusqu'à la décision d'un MODERATOR (jamais sur sa propre
 * contribution). Unicité et recherche sur une forme normalisée (sans casse ni accents).
 */
@Service
@RequiredArgsConstructor
public class JargonService {

    public static final String ROLES = "jargon_roles";
    public static final String MODERATOR = "MODERATOR";
    static final int MAX_SUGGESTIONS = 10;

    private final JargonTermRepository terms;
    private final JargonDefinitionRepository definitions;
    private final JargonVoteRepository votes;
    private final MissingSearchRepository missing;
    private final AppRoles roles;
    private final UserLookup userLookup;

    /** Minuscules, sans accents ni espaces superflus : « Réseau » et « reseau » sont le même terme. */
    public static String normalize(String s) {
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    public List<String> myRoles(AuthUser me) {
        return roles.rolesOf(ROLES, me, Set.of(MODERATOR));
    }

    // ---------- lecture ----------

    /** Autocomplétion sur le préfixe ; une recherche sans résultat est comptée (anonymement). */
    @Transactional
    public List<Suggestion> suggest(String q, int limit) {
        if (q == null || q.isBlank()) {
            throw ValidationException.field("q", "au moins 1 caractère");
        }
        String prefix = normalize(q);
        if (prefix.length() > 60) {
            throw ValidationException.field("q", "60 caractères maximum");
        }
        int size = Math.min(Math.max(1, limit), MAX_SUGGESTIONS);
        String escaped = prefix.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        List<Suggestion> list = terms.suggest(escaped, PageRequest.of(0, size)).stream().map(this::suggestion).toList();
        if (list.isEmpty()) {
            missing.hit(prefix, q.trim());
        }
        return list;
    }

    @Transactional(readOnly = true)
    public PageResponse<TermSummary> list(String search, String letter, String team, int page, int size) {
        String s = search == null || search.isBlank() ? null : normalize(search);
        String l = letter == null || letter.isBlank() ? null : normalize(letter).substring(0, 1);
        String t = team == null || team.isBlank() ? null : normalize(team);
        List<TermSummary> all = terms.findByStatusOrderByNormalizedAsc(ContributionStatus.PUBLISHED).stream()
                .filter(x -> s == null || x.getNormalized().contains(s) || (x.getExpansion() != null && normalize(x.getExpansion()).contains(s)))
                .filter(x -> l == null || x.getNormalized().startsWith(l))
                .filter(x -> t == null || (x.getTeam() != null && normalize(x.getTeam()).equals(t)))
                .map(x -> new TermSummary(x.getId(), x.getTerm(), x.getExpansion(), x.getTeam(), published(x.getId()).size()))
                .toList();
        return PageResponse.ofList(all, page, Math.min(Math.max(1, size), 100));
    }

    @Transactional(readOnly = true)
    public TermDto get(AuthUser me, UUID id) {
        JargonTerm t = publishedTerm(id);
        List<DefinitionDto> defs = published(id).stream().map(d -> {
            long helpful = votes.countByIdDefinitionId(d.getId());
            boolean voted = votes.existsById(new JargonVote.Key(d.getId(), me.id()));
            return new DefinitionDto(d.getId(), d.getText(), d.getExample(), d.getAuthorId() == null ? null : userLookup.name(d.getAuthorId()),
                    helpful, voted, me.id().equals(d.getAuthorId()));
        }).sorted(Comparator.comparingLong(DefinitionDto::helpful).reversed()).toList();
        List<Suggestion> related = terms.findAllById(t.getRelatedIds()).stream().filter(r -> r.getStatus() == ContributionStatus.PUBLISHED)
                .sorted(Comparator.comparing(JargonTerm::getNormalized)).map(this::suggestion).toList();
        return new TermDto(t.getId(), t.getTerm(), t.getExpansion(), t.getTeam(), defs, related);
    }

    // ---------- contributions ----------

    @Transactional
    public ContributionDto proposeTerm(AuthUser me, TermRequest req) {
        String normalized = normalize(req.term());
        if (terms.existsByNormalizedAndStatusNot(normalized, ContributionStatus.REJECTED)) {
            throw ApiException.conflict("TERM_EXISTS", "Ce terme existe déjà ou est en cours de modération");
        }
        Set<UUID> related = new HashSet<>(req.relatedTermIds() == null ? List.of() : req.relatedTermIds());
        for (UUID r : related) {
            if (terms.findById(r).filter(x -> x.getStatus() == ContributionStatus.PUBLISHED).isEmpty()) {
                throw ValidationException.field("relatedTermIds", "terme lié introuvable ou non publié");
            }
        }
        JargonTerm t = new JargonTerm();
        t.setTerm(req.term().trim());
        t.setNormalized(normalized);
        t.setExpansion(blankToNull(req.expansion()));
        t.setTeam(blankToNull(req.teamOfOrigin()));
        t.setStatus(ContributionStatus.PENDING);
        t.setAuthorId(me.id());
        t.setRelatedIds(related);
        try {
            terms.saveAndFlush(t);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("TERM_EXISTS", "Ce terme vient d'être proposé");
        }
        JargonDefinition d = newDefinition(me, t.getId(), req.definition(), req.example());
        return termContribution(t, d);
    }

    @Transactional
    public ContributionDto proposeDefinition(AuthUser me, UUID termId, DefinitionRequest req) {
        JargonTerm t = publishedTerm(termId);
        return definitionContribution(t, newDefinition(me, t.getId(), req.text(), req.example()));
    }

    @Transactional(readOnly = true)
    public List<ContributionDto> mine(AuthUser me) {
        List<JargonTerm> myTerms = terms.findByAuthorIdOrderByCreatedAtDesc(me.id());
        Set<UUID> myTermIds = myTerms.stream().map(JargonTerm::getId).collect(java.util.stream.Collectors.toSet());
        Stream<ContributionDto> a = myTerms.stream().map(t -> termContribution(t, initial(t)));
        Stream<ContributionDto> b = definitions.findByAuthorIdOrderByCreatedAtDesc(me.id()).stream()
                .filter(d -> !myTermIds.contains(d.getTermId()) || !initial(term(d.getTermId())).getId().equals(d.getId()))
                .map(d -> definitionContribution(term(d.getTermId()), d));
        return Stream.concat(a, b).sorted(Comparator.comparing(ContributionDto::createdAt).reversed()).toList();
    }

    // ---------- votes ----------

    @Transactional
    public long vote(AuthUser me, UUID definitionId) {
        JargonDefinition d = publishedDefinition(definitionId);
        if (me.id().equals(d.getAuthorId())) {
            throw ApiException.conflict("OWN_DEFINITION", "Vous ne pouvez pas voter pour votre propre définition");
        }
        JargonVote.Key key = new JargonVote.Key(definitionId, me.id());
        if (!votes.existsById(key)) {
            votes.saveAndFlush(new JargonVote(key));
        }
        return votes.countByIdDefinitionId(definitionId);
    }

    @Transactional
    public long unvote(AuthUser me, UUID definitionId) {
        publishedDefinition(definitionId);
        votes.deleteById(new JargonVote.Key(definitionId, me.id()));
        votes.flush();
        return votes.countByIdDefinitionId(definitionId);
    }

    // ---------- modération ----------

    /** Termes en attente (avec leur définition initiale) et définitions en attente sur des termes publiés. */
    @Transactional(readOnly = true)
    public List<ContributionDto> moderation(AuthUser me) {
        roles.require(ROLES, me, MODERATOR);
        Stream<ContributionDto> a = terms.findByStatusOrderByCreatedAtAsc(ContributionStatus.PENDING).stream().map(t -> termContribution(t, initial(t)));
        Stream<ContributionDto> b = definitions.findByStatusOrderByCreatedAtAsc(ContributionStatus.PENDING).stream()
                .filter(d -> term(d.getTermId()).getStatus() == ContributionStatus.PUBLISHED)
                .map(d -> definitionContribution(term(d.getTermId()), d));
        return Stream.concat(a, b).sorted(Comparator.comparing(ContributionDto::createdAt)).toList();
    }

    @Transactional
    public ContributionDto approve(AuthUser me, UUID id) {
        return decide(me, id, ContributionStatus.PUBLISHED, null);
    }

    @Transactional
    public ContributionDto reject(AuthUser me, UUID id, String reason) {
        return decide(me, id, ContributionStatus.REJECTED, reason.trim());
    }

    private ContributionDto decide(AuthUser me, UUID id, ContributionStatus status, String reason) {
        roles.require(ROLES, me, MODERATOR);
        Optional<JargonTerm> term = terms.findById(id);
        if (term.isPresent()) {
            JargonTerm t = term.get();
            JargonDefinition d = initial(t);
            pending(me, t.getStatus(), t.getAuthorId());
            t.setStatus(status);
            t.setReason(reason);
            d.setStatus(status);
            d.setReason(reason);
            terms.saveAndFlush(t);
            definitions.saveAndFlush(d);
            return termContribution(t, d);
        }
        JargonDefinition d = definitions.findById(id).orElseThrow(() -> ApiException.notFound("Contribution"));
        JargonTerm t = term(d.getTermId());
        if (t.getStatus() != ContributionStatus.PUBLISHED) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Modérez d'abord le terme");
        }
        pending(me, d.getStatus(), d.getAuthorId());
        d.setStatus(status);
        d.setReason(reason);
        definitions.saveAndFlush(d);
        return definitionContribution(t, d);
    }

    private void pending(AuthUser me, ContributionStatus status, UUID authorId) {
        if (status != ContributionStatus.PENDING) {
            throw ApiException.conflict("INVALID_STATUS_TRANSITION", "Contribution déjà " + status);
        }
        if (me.id().equals(authorId)) {
            throw ApiException.conflict("SELF_MODERATION", "Vous ne pouvez pas modérer votre propre contribution");
        }
    }

    @Transactional(readOnly = true)
    public List<MissingDto> missing(AuthUser me) {
        me.requireAdmin();
        return missing.findTop50ByOrderByHitsDescLastAtDesc().stream().map(m -> new MissingDto(m.getQuery(), m.getHits(), m.getLastAt())).toList();
    }

    // ---------- outils ----------

    private JargonDefinition newDefinition(AuthUser me, UUID termId, String text, String example) {
        JargonDefinition d = new JargonDefinition();
        d.setTermId(termId);
        d.setText(text.trim());
        d.setExample(blankToNull(example));
        d.setStatus(ContributionStatus.PENDING);
        d.setAuthorId(me.id());
        return definitions.saveAndFlush(d);
    }

    /** Définition déposée avec le terme : la plus ancienne. */
    private JargonDefinition initial(JargonTerm t) {
        return definitions.findByTermId(t.getId()).stream().min(Comparator.comparing(JargonDefinition::getCreatedAt).thenComparing(JargonDefinition::getId))
                .orElseThrow();
    }

    private List<JargonDefinition> published(UUID termId) {
        return definitions.findByTermId(termId).stream().filter(d -> d.getStatus() == ContributionStatus.PUBLISHED).toList();
    }

    private JargonTerm term(UUID id) {
        return terms.findById(id).orElseThrow(() -> ApiException.notFound("Terme"));
    }

    private JargonTerm publishedTerm(UUID id) {
        return terms.findById(id).filter(t -> t.getStatus() == ContributionStatus.PUBLISHED).orElseThrow(() -> ApiException.notFound("Terme"));
    }

    private JargonDefinition publishedDefinition(UUID id) {
        return definitions.findById(id).filter(d -> d.getStatus() == ContributionStatus.PUBLISHED).orElseThrow(() -> ApiException.notFound("Définition"));
    }

    private Suggestion suggestion(JargonTerm t) {
        return new Suggestion(t.getId(), t.getTerm(), t.getExpansion());
    }

    private ContributionDto termContribution(JargonTerm t, JargonDefinition d) {
        return new ContributionDto(t.getId(), "TERM", t.getId(), t.getTerm(), t.getExpansion(), d.getText(), d.getExample(),
                t.getAuthorId() == null ? null : userLookup.name(t.getAuthorId()), t.getStatus(), t.getReason(), t.getCreatedAt());
    }

    private ContributionDto definitionContribution(JargonTerm t, JargonDefinition d) {
        return new ContributionDto(d.getId(), "DEFINITION", t.getId(), t.getTerm(), t.getExpansion(), d.getText(), d.getExample(),
                d.getAuthorId() == null ? null : userLookup.name(d.getAuthorId()), d.getStatus(), d.getReason(), d.getCreatedAt());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
